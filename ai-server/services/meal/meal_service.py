"""
식단(음식) 관련 AI 서비스
- (Gemini) Vision으로 음식명 후보 추출 (ANALYZE_IMAGE)
- (Gemini) 식단 생성/재분배/조언 (GENERATE/GENERATE_WEEK/GENERATE_MONTH/REPLAN/ADVICE)
- (Qdrant) 음식명 기반 영양정보 조회 (정확 매칭 우선)
"""

from __future__ import annotations

import os
import re
import datetime
import time
import random
import difflib
from typing import Any, Dict, List, Optional, Tuple

from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue

from prompts.meal.meal_vision_keyword import SYSTEM_PROMPT as VISION_SYSTEM_PROMPT, get_vision_prompt
from prompts.meal.meal_generate import (
    SYSTEM_PROMPT as GEN_SYSTEM_PROMPT,
    get_generate_prompt,
    get_generate_week_prompt,
    get_generate_month_prompt,
    get_generate_days_prompt,
)
from prompts.meal.meal_target_inference import SYSTEM_PROMPT as TARGET_SYSTEM_PROMPT, get_target_inference_prompt
from prompts.meal.meal_replan import SYSTEM_PROMPT as REPLAN_SYSTEM_PROMPT, get_replan_prompt
from prompts.meal.meal_advice import SYSTEM_PROMPT as ADVICE_SYSTEM_PROMPT, get_advice_prompt

from services.ai_service import call_meal_ai_json, call_ai_vision_json

# NOTE: meal_foods / meal_templates 컬렉션은 payload-only(dummy vector size=1)로 운영합니다.
# 따라서 벡터 검색은 사용하지 않고, exact + 유사도(문자열) 기반으로 best-match를 선택합니다.

_FOOD_CACHE: Optional[List[Dict[str, Any]]] = None
_FOOD_CACHE_AT: float = 0.0


def _load_food_cache() -> List[Dict[str, Any]]:
    global _FOOD_CACHE, _FOOD_CACHE_AT
    ttl = float(os.getenv("MEAL_FOOD_CACHE_TTL_SECONDS", "60"))
    now = time.time()
    if _FOOD_CACHE is not None and (now - _FOOD_CACHE_AT) < ttl:
        return _FOOD_CACHE

    client = _get_qdrant_client()
    collection = _meal_collection()
    items: List[Dict[str, Any]] = []
    next_offset = None
    for _ in range(200):
        recs, next_offset = client.scroll(
            collection_name=collection,
            limit=200,
            offset=next_offset,
            with_payload=True,
            with_vectors=False,
        )
        if not recs:
            break
        for r in recs:
            p = r.payload or {}
            if not p:
                continue
            # keep only needed keys
            items.append(
                {
                    "foodName": p.get("foodName"),
                    "foodNameNormalizedStatus": p.get("foodNameNormalizedStatus"),
                    "foodNameNormalized": p.get("foodNameNormalized"),
                    "displayName": p.get("displayName") or p.get("foodName"),
                    **_extract_macros(p),
                    "servingSize": _extract_serving_size(p),
                }
            )
        if next_offset is None:
            break

    _FOOD_CACHE = items
    _FOOD_CACHE_AT = now
    return items


def _normalize_food_name(name: str) -> str:
    if not name:
        return ""
    s = str(name).strip().lower()
    # 괄호 내 서빙 정보 제거 (예: "김치찌개 (1인분)" -> "김치찌개")
    s = re.sub(r"\([^)]*\)", "", s).strip()
    s = re.sub(r"\s+", "", s)
    s = s.replace("-", "")
    return s


def _get_qdrant_client() -> QdrantClient:
    url = os.getenv("QDRANT_URL", "http://localhost:6333")
    timeout_sec = float(os.getenv("QDRANT_TIMEOUT_SECONDS", "5"))
    return QdrantClient(url=url, timeout=timeout_sec)


def _meal_collection() -> str:
    """
    Meal은 Gemini 임베딩(기본 768)을 쓰므로, 별도 컬렉션을 사용합니다.
    기존 OpenAI(1536) 컬렉션은 그대로 보존합니다.
    """
    # Excel 기반 컬렉션 대신, 템플릿에서 파생된 음식 DB를 기본으로 사용
    return os.getenv("MEAL_FOOD_QDRANT_COLLECTION", "meal_foods")


def _templates_collection() -> str:
    return os.getenv("MEAL_TEMPLATES_COLLECTION", "meal_templates")


def _payload_number(payload: Dict[str, Any], keys: List[str]) -> Optional[float]:
    for k in keys:
        if k in payload and payload[k] is not None:
            try:
                return float(payload[k])
            except Exception:
                continue
    return None


def _extract_macros(payload: Dict[str, Any]) -> Dict[str, int]:
    cal = _payload_number(payload, ["calories", "kcal"])
    carbs = _payload_number(payload, ["carbs", "carbohydrate"])
    protein = _payload_number(payload, ["protein"])
    fat = _payload_number(payload, ["fat"])

    if cal is None:
        cal = _payload_number(payload, ["에너지(kcal)", "열량(kcal)", "칼로리(kcal)", "칼로리", "에너지"])
    if carbs is None:
        carbs = _payload_number(payload, ["탄수화물(g)", "탄수화물"])
    if protein is None:
        protein = _payload_number(payload, ["단백질(g)", "단백질"])
    if fat is None:
        fat = _payload_number(payload, ["지방(g)", "지방"])

    def _to_int(x: Optional[float]) -> int:
        if x is None:
            return 0
        try:
            return int(round(float(x)))
        except Exception:
            return 0

    return {
        "calories": _to_int(cal),
        "carbs": _to_int(carbs),
        "protein": _to_int(protein),
        "fat": _to_int(fat),
    }


def _extract_serving_size(payload: Dict[str, Any]) -> Optional[str]:
    """
    Qdrant payload에 저장된 '1인분 기준' 텍스트(예: "1인분", "1개", "1그릇")를 추출합니다.
    - 그램(g) 파싱/계산은 하지 않습니다. (요구사항: 그램수는 빼기)
    """
    if not payload:
        return None
    for key in [
        "servingSize",
        "serving_size",
        "serving",
        "portion",
        "기준량",
        "1인분",
        "제공량",
        "1회제공량",
        "1회 제공량",
    ]:
        v = payload.get(key)
        if v is None:
            continue
        s = str(v).strip()
        if s and s.lower() != "nan":
            return s
    return None


def lookup_food_details(
    food_name: str, extra_queries: Optional[List[str]] = None
) -> Tuple[str, Dict[str, int], Optional[str]]:
    """
    영양(macros) + (선택) servingSize 텍스트까지 함께 반환합니다.
    - servingSize는 DB(payload)에 저장돼 있는 경우에만 사용 (LLM 추정치로 계산하지 않음)
    """
    queries: List[str] = []
    if food_name:
        queries.append(food_name)
    if extra_queries:
        for q in extra_queries:
            if q and q not in queries:
                queries.append(q)

    # exact 우선
    for q in queries:
        norm = _normalize_food_name(q)
        if not norm:
            continue
        payload = _exact_lookup_food(norm)
        if payload:
            return (
                payload.get("foodName") or q,
                _extract_macros(payload),
                _extract_serving_size(payload),
            )

    # fuzzy fallback (payload-only)
    for q in queries:
        payload = _fuzzy_lookup_food(q)
        if payload:
            return (
                payload.get("foodName") or q,
                {
                    "calories": int(payload.get("calories", 0) or 0),
                    "carbs": int(payload.get("carbs", 0) or 0),
                    "protein": int(payload.get("protein", 0) or 0),
                    "fat": int(payload.get("fat", 0) or 0),
                },
                payload.get("servingSize") or "1인분",
            )

    return food_name or "알 수 없음", {"calories": 0, "carbs": 0, "protein": 0, "fat": 0}, None


def _exact_lookup_food(payload_key_normalized: str) -> Optional[Dict[str, Any]]:
    client = _get_qdrant_client()
    collection = _meal_collection()
    try:
        records, _next = client.scroll(
            collection_name=collection,
            scroll_filter=Filter(
                must=[
                    FieldCondition(
                        key="foodNameNormalized",
                        match=MatchValue(value=payload_key_normalized),
                    )
                ]
            ),
            limit=1,
            with_payload=True,
            with_vectors=False,
        )
        if not records:
            return None
        return records[0].payload or None
    except Exception as e:
        print(f"[meal_service] Qdrant exact lookup failed: {e}")
        return None


def _vector_lookup_food(query: str) -> Optional[Dict[str, Any]]:
    # payload-only 컬렉션이라 vector search는 쓰지 않습니다.
    return None


def _fuzzy_lookup_food(query: str) -> Optional[Dict[str, Any]]:
    qn = _normalize_food_name(query)
    if not qn:
        return None
    foods = _load_food_cache()
    if not foods:
        return None

    best = None
    best_score = -1.0
    # 1) 포함 관계 우선
    for f in foods:
        kn = str(f.get("foodNameNormalized") or "")
        if not kn:
            continue
        if qn == kn:
            return f
        if qn in kn or kn in qn:
            score = 0.9 + min(len(qn), len(kn)) / max(1, max(len(qn), len(kn))) * 0.1
            if score > best_score:
                best_score = score
                best = f

    # 2) 유사도(SequenceMatcher)로 best-match
    for f in foods:
        kn = str(f.get("foodNameNormalized") or "")
        if not kn:
            continue
        score = difflib.SequenceMatcher(a=qn, b=kn).ratio()
        if score > best_score:
            best_score = score
            best = f

    # 너무 낮으면 실패 처리
    min_score = float(os.getenv("MEAL_FOOD_FUZZY_MIN_SCORE", "0.55"))
    if best is not None and best_score >= min_score:
        return best
    return None


def lookup_food_nutrition(food_name: str, extra_queries: Optional[List[str]] = None) -> Tuple[str, Dict[str, int]]:
    t0 = time.perf_counter()
    queries: List[str] = []
    if food_name:
        queries.append(food_name)
        # 괄호 제거 변형도 함께 시도
        base = re.sub(r"\s*\([^)]*\)\s*", "", str(food_name)).strip()
        if base and base not in queries:
            queries.append(base)
    if extra_queries:
        for q in extra_queries:
            if q and q not in queries:
                queries.append(q)

    for q in queries:
        t_exact = time.perf_counter()
        norm = _normalize_food_name(q)
        if not norm:
            continue
        payload = _exact_lookup_food(norm)
        if payload:
            print(f"[meal_service] Qdrant exact lookup took {(time.perf_counter() - t_exact) * 1000:.1f} ms")
            return payload.get("foodName") or q, _extract_macros(payload)
        print(f"[meal_service] Qdrant exact lookup took {(time.perf_counter() - t_exact) * 1000:.1f} ms (miss)")

    for q in queries:
        t_fz = time.perf_counter()
        payload = _fuzzy_lookup_food(q)
        if payload:
            print(f"[meal_service] Qdrant fuzzy lookup took {(time.perf_counter() - t_fz) * 1000:.1f} ms")
            return payload.get("foodName") or q, _extract_macros(payload)
        print(f"[meal_service] Qdrant fuzzy lookup took {(time.perf_counter() - t_fz) * 1000:.1f} ms (miss)")

    print(f"[meal_service] Total nutrition lookup took {(time.perf_counter() - t0) * 1000:.1f} ms")
    return food_name or "알 수 없음", {"calories": 0, "carbs": 0, "protein": 0, "fat": 0}


def _infer_daily_targets(profile: Dict[str, Any], goal: Dict[str, Any]) -> Dict[str, int]:
    """
    Gemini로 '하루 목표 영양(칼/탄/단/지)'만 추론합니다.
    - 실제 음식 영양은 템플릿/DB 값 그대로 사용.
    """

    def _as_float(v: Any) -> Optional[float]:
        try:
            if v is None:
                return None
            return float(v)
        except Exception:
            return None

    def _estimate_by_formula(p: Dict[str, Any], gt: str) -> Dict[str, int]:
        """
        안정적인 fallback 목표치(일반적 가이드) - Mifflin-St Jeor + 활동계수 + 목표 보정
        """
        age = _as_float((p or {}).get("age")) or 0.0
        height = _as_float((p or {}).get("height")) or 0.0  # cm
        weight = _as_float((p or {}).get("weight")) or 0.0  # kg
        gender = str((p or {}).get("gender") or "").upper()

        # 활동계수 (memberinfo가 없으면 MODERATE로 취급)
        activity = str((p or {}).get("activityLevel") or "MODERATE").upper()
        af = {
            "LOW": 1.2,
            "SEDENTARY": 1.2,
            "LIGHT": 1.375,
            "MODERATE": 1.55,
            "ACTIVE": 1.725,
            "VERY_ACTIVE": 1.9,
        }.get(activity, 1.55)

        # BMR
        if height > 0 and weight > 0 and age > 0:
            base = 10 * weight + 6.25 * height - 5 * age
            if gender in ("MALE", "M", "MAN", "남", "남성"):
                bmr = base + 5
            elif gender in ("FEMALE", "F", "WOMAN", "여", "여성"):
                bmr = base - 161
            else:
                # 성별 불명확하면 중간값
                bmr = base - 78
            tdee = bmr * af
        else:
            # 프로필 부족 시 보수적 기본값
            tdee = 2300.0

        gt_u = str(gt or "MAINTAIN").upper()
        if gt_u == "DIET":
            target_cal = int(round(tdee * 0.85))
        elif gt_u == "BULK_UP":
            target_cal = int(round(tdee * 1.10))
        else:
            target_cal = int(round(tdee))

        # 단백질: 체중 기반 (벌크업/근육량↑ 우선)
        if weight <= 0:
            protein_g = 130 if gt_u == "BULK_UP" else 110
        else:
            per_kg = 1.8 if gt_u == "BULK_UP" else (1.7 if gt_u == "DIET" else 1.6)
            protein_g = int(round(weight * per_kg))

        # 지방: 최소 0.6g/kg 또는 총열량의 25% 중 큰 값(과도한 저지방 방지)
        if weight > 0:
            fat_min = int(round(weight * 0.6))
        else:
            fat_min = 50
        fat_by_cal = int(round((target_cal * 0.25) / 9))
        fat_g = max(fat_min, fat_by_cal)

        # 탄수: 나머지
        carbs_g = int(round(max(0, target_cal - protein_g * 4 - fat_g * 9) / 4))

        # 최소 칼로리 안전장치
        target_cal = max(target_cal, 1400 if gt_u == "DIET" else 1600)
        return {"targetCalories": target_cal, "targetCarbs": carbs_g, "targetProtein": protein_g, "targetFat": fat_g}

    goal_type = (goal or {}).get("goalType") or (goal or {}).get("goal_type") or "MAINTAIN"
    baseline = _estimate_by_formula(profile or {}, str(goal_type))

    # AI 추론 시도 (간소화된 프롬프트 사용)
    try:
        raw = call_meal_ai_json(
            system_prompt=TARGET_SYSTEM_PROMPT,
            user_prompt=get_target_inference_prompt(profile or {}, str(goal_type)),
            temperature=0.1,
            timeout_seconds=15.0,  # 타임아웃 단축
        )

        def _i(k: str) -> int:
            try:
                return int(raw.get(k, 0) or 0)
            except Exception:
                return 0

        inferred = {
            "targetCalories": _i("targetCalories"),
            "targetCarbs": _i("targetCarbs"),
            "targetProtein": _i("targetProtein"),
            "targetFat": _i("targetFat"),
        }

        # AI 추론 결과 검증: baseline ±20% 범위 내로 클램프
        if inferred["targetCalories"] > 0 and (inferred["targetCarbs"] + inferred["targetProtein"] + inferred["targetFat"]) > 0:
            lo = int(round(baseline["targetCalories"] * 0.8))
            hi = int(round(baseline["targetCalories"] * 1.2))
            inferred["targetCalories"] = max(lo, min(hi, inferred["targetCalories"]))
            
            # 매크로 칼로리 합 검증
            macro_kcal = inferred["targetCarbs"] * 4 + inferred["targetProtein"] * 4 + inferred["targetFat"] * 9
            if macro_kcal > 0:
                diff_ratio = abs(macro_kcal - inferred["targetCalories"]) / float(inferred["targetCalories"] or 1)
                if diff_ratio <= 0.25:  # 25% 이내 차이면 유효
                    return inferred
        
        # AI 추론 실패 시 baseline 사용
        return baseline
    except Exception:
        # AI 추론 실패 시 baseline 사용
        return baseline


def _load_templates_from_qdrant() -> List[Dict[str, Any]]:
    client = _get_qdrant_client()
    collection = _templates_collection()
    templates: List[Dict[str, Any]] = []
    next_offset = None
    for _ in range(200):
        recs, next_offset = client.scroll(
            collection_name=collection,
            limit=200,
            offset=next_offset,
            with_payload=True,
            with_vectors=False,
        )
        if not recs:
            break
        for r in recs:
            p = r.payload or {}
            items = p.get("items") or []
            totals = p.get("totals") or {}
            if not isinstance(items, list) or not items:
                continue
            templates.append(
                {
                    "templateId": p.get("templateId") or str(r.id),
                    "items": items,
                    "totals": {
                        "calories": int(round(float(totals.get("calories", 0) or 0))),
                        "carbs": int(round(float(totals.get("carbs", 0) or 0))),
                        "protein": int(round(float(totals.get("protein", 0) or 0))),
                        "fat": int(round(float(totals.get("fat", 0) or 0))),
                    },
                }
            )
        if next_offset is None:
            break
    return templates


def _combine_totals(a: Dict[str, int], b: Dict[str, int]) -> Dict[str, int]:
    return {
        "calories": a.get("calories", 0) + b.get("calories", 0),
        "carbs": a.get("carbs", 0) + b.get("carbs", 0),
        "protein": a.get("protein", 0) + b.get("protein", 0),
        "fat": a.get("fat", 0) + b.get("fat", 0),
    }


def _score_totals(totals: Dict[str, int], target: Dict[str, int]) -> float:
    # 칼로리 우선, 탄단지 보조
    return (
        abs(totals.get("calories", 0) - target.get("targetCalories", 0)) * 2.0
        + abs(totals.get("carbs", 0) - target.get("targetCarbs", 0)) * 1.0
        + abs(totals.get("protein", 0) - target.get("targetProtein", 0)) * 1.2
        + abs(totals.get("fat", 0) - target.get("targetFat", 0)) * 1.0
    )


def _pick_day_templates(
    templates: List[Dict[str, Any]],
    target: Dict[str, int],
    *,
    rng: random.Random,
    cooldown_days: int,
    last_used_day: Dict[str, int],
    day_index: int,
) -> List[Dict[str, Any]]:
    eligible = [
        t
        for t in templates
        if (day_index - last_used_day.get(str(t.get("templateId")), -10**9)) >= cooldown_days
    ]
    if len(eligible) < 3:
        eligible = templates[:]

    per_meal_cal = max(1, int(target.get("targetCalories", 0) / 3)) if target.get("targetCalories") else 500
    eligible_sorted = sorted(eligible, key=lambda t: abs(t["totals"]["calories"] - per_meal_cal))
    pool = eligible_sorted[: min(60, len(eligible_sorted))]
    if len(pool) < 3:
        pool = eligible_sorted

    best = None
    best_score = float("inf")
    trials = min(4000, max(200, len(pool) * 50))

    for _ in range(trials):
        a, b, c = rng.sample(pool, 3)
        if a["templateId"] == b["templateId"] or a["templateId"] == c["templateId"] or b["templateId"] == c["templateId"]:
            continue
        day_totals = _combine_totals(_combine_totals(a["totals"], b["totals"]), c["totals"])
        sc = _score_totals(day_totals, target)
        if sc < best_score:
            best_score = sc
            best = (a, b, c)

    if best:
        return [best[0], best[1], best[2]]
    return rng.sample(pool, 3)


def analyze_food_image(base64_image: str) -> Dict[str, Any]:
    t_vision = time.perf_counter()
    vision = call_ai_vision_json(
        system_prompt=VISION_SYSTEM_PROMPT,
        user_prompt=get_vision_prompt(),
        image_base64=base64_image,
        temperature=0.2,
    )
    print(f"[meal_service] Gemini vision JSON took {(time.perf_counter() - t_vision) * 1000:.1f} ms")

    food_candidates = vision.get("food_candidates") or []
    rag_queries = vision.get("rag_queries") or []

    best_name = None
    if isinstance(food_candidates, list) and food_candidates:
        top = food_candidates[0] if isinstance(food_candidates[0], dict) else None
        if top:
            best_name = top.get("name")
    best_name = best_name or (rag_queries[0] if rag_queries else "알 수 없음")

    resolved_name, macros = lookup_food_nutrition(best_name, extra_queries=rag_queries)
    return {
        "analyzedFood": {
            "foodName": resolved_name,
            **macros,
        }
    }


def generate_meal_plan(profile: Dict[str, Any], goal: Dict[str, Any]) -> Dict[str, Any]:
    # 1일 = 3끼 템플릿 배치 (간식 없음)
    start_date = (goal or {}).get("startDate") or (goal or {}).get("start_date")
    return generate_meal_plan_days(profile, goal, 1, start_date=start_date)


def generate_meal_plan_week(profile: Dict[str, Any], goal: Dict[str, Any]) -> Dict[str, Any]:
    start_date = (goal or {}).get("startDate") or (goal or {}).get("start_date")
    return generate_meal_plan_days(profile, goal, 7, start_date=start_date)


def generate_meal_plan_month(profile: Dict[str, Any], goal: Dict[str, Any]) -> Dict[str, Any]:
    start_date = (goal or {}).get("startDate") or (goal or {}).get("start_date")
    return generate_meal_plan_days(profile, goal, 30, start_date=start_date)


def generate_meal_plan_days(profile: Dict[str, Any], goal: Dict[str, Any], days: int, *, start_date: Optional[str] = None) -> Dict[str, Any]:
    # start_date는 YYYY-MM-DD. 없으면 today.
    start = datetime.date.today()
    if start_date:
        try:
            start = datetime.date.fromisoformat(str(start_date).strip())
        except Exception:
            start = datetime.date.today()
    total_days = max(1, int(days or 1))
    # 1) Gemini로 목표치만 추론
    target = _infer_daily_targets(profile or {}, goal or {})

    # 2) Qdrant 템플릿 풀 로드
    templates = _load_templates_from_qdrant()
    if not templates:
        # 템플릿이 아직 없으면 기존 LLM 생성으로 fallback
        raw = call_meal_ai_json(
            system_prompt=GEN_SYSTEM_PROMPT,
            user_prompt=get_generate_days_prompt(profile, goal, total_days),
            temperature=0.3,
        )
        days_meals = raw.get("daysMeals") or []
        suggested: List[Dict[str, Any]] = []
        for day_data in days_meals:
            try:
                day_offset = int(day_data.get("dayOffset", 0) or 0)
            except Exception:
                day_offset = 0
            meal_date = (start + datetime.timedelta(days=day_offset)).isoformat()
            meals = day_data.get("meals") or []
            for m in meals:
                meal_time = (m.get("mealTime") or "BREAKFAST").upper()
                food_name = m.get("foodName") or "알 수 없음"
                resolved_name, macros, db_serving = lookup_food_details(food_name, extra_queries=[])
                serving = db_serving or (m.get("servingSize") or "1인분")
                suggested.append(
                    {
                        "mealDate": meal_date,
                        "mealTime": meal_time,
                        "status": "PLANNED",
                        "isAdditional": False,
                        "foodName": resolved_name,
                        "servingSize": serving,
                        **macros,
                        "originalFoodName": resolved_name,
                        "originalServingSize": serving,
                        "originalCalories": macros["calories"],
                        "originalCarbs": macros["carbs"],
                        "originalProtein": macros["protein"],
                        "originalFat": macros["fat"],
                    }
                )
        return {"suggestedMeals": suggested, "target": target}

    # 3) 템플릿 배치(목표 맞춤 + 중복 최소화)
    rng = random.Random()
    rng.seed(hash((start.isoformat(), (goal or {}).get("goalType") or "MAINTAIN")))
    cooldown_days = int(os.getenv("MEAL_TEMPLATE_COOLDOWN_DAYS", "7"))
    last_used_day: Dict[str, int] = {}

    suggested: List[Dict[str, Any]] = []
    for day_index in range(total_days):
        meal_date = (start + datetime.timedelta(days=day_index)).isoformat()
        picked = _pick_day_templates(
            templates,
            target,
            rng=rng,
            cooldown_days=cooldown_days,
            last_used_day=last_used_day,
            day_index=day_index,
        )

        day_meals_temp = []
        
        # 3-1) 템플릿 1차 배치
        for meal_time, tpl in zip(["BREAKFAST", "LUNCH", "DINNER"], picked):
            tid = str(tpl.get("templateId"))
            last_used_day[tid] = day_index

            items = tpl.get("items") or []
            for it in items:
                food_name = str(it.get("foodName") or "알 수 없음").strip()
                macros = {
                    "calories": int(round(float(it.get("calories", 0) or 0))),
                    "carbs": int(round(float(it.get("carbs", 0) or 0))),
                    "protein": int(round(float(it.get("protein", 0) or 0))),
                    "fat": int(round(float(it.get("fat", 0) or 0))),
                }
                serving = "1인분"
                day_meals_temp.append(
                    {
                        "mealDate": meal_date,
                        "mealTime": meal_time,
                        "status": "PLANNED",
                        "isAdditional": False,
                        "foodName": food_name,
                        "servingSize": serving,
                        **macros,
                        "originalFoodName": food_name,
                        "originalServingSize": serving,
                        "originalCalories": macros["calories"],
                        "originalCarbs": macros["carbs"],
                        "originalProtein": macros["protein"],
                        "originalFat": macros["fat"],
                    }
                )

        # 3-2) 목표 대비 보정 (Gap Filling / Trimming)
        _adjust_daily_meals_to_target(day_meals_temp, target, rng)
        
        suggested.extend(day_meals_temp)

    return {"suggestedMeals": suggested, "target": target}


def _adjust_daily_meals_to_target(
    day_meals: List[Dict[str, Any]],
    target: Dict[str, int],
    rng: random.Random
) -> None:
    """
    템플릿 1차 배치 이후 보정:
    - 1차: 칼로리 목표를 우선 맞춥니다.
    - 2차: 남은 탄/단/지 오차를 줄입니다. (특히 탄수 부족이면 주식/면/죽 허용)
    - 제약:
      - 끼니별 국물(국/찌개/탕) 1개만 허용
      - 끼니별 주식(밥/죽/면) 기본 1개만 허용
      - 단, '탄수 크게 부족'할 때는 '밥이 있는 끼니'에 한해 '죽/면'을 추가로 허용
    """
    if not day_meals:
        return

    # ---------------------------
    # 키워드/헬퍼
    # ---------------------------
    STAPLE_KW = ["밥", "죽", "면", "국수", "라면", "우동"]
    NOODLE_PORRIDGE_KW = ["죽", "면", "국수", "라면", "우동"]
    SOUP_KW = ["국", "찌개", "탕"]

    PRESERVE_KW = ["밥", "국", "찌개", "탕"]  # 제거 단계에서 보존

    # "탄수 부족" 예외 허용 임계치 (g)
    CARB_DEFICIT_ALLOW_SECOND_STAPLE = int(os.getenv("MEAL_CARB_DEFICIT_ALLOW_SECOND_STAPLE_G", "40"))

    # 매크로 미세 조정 최소 단위(노이즈 방지)
    MACRO_CARB_EPS = int(os.getenv("MEAL_MACRO_CARB_EPS_G", "25"))
    MACRO_PROT_EPS = int(os.getenv("MEAL_MACRO_PROT_EPS_G", "15"))
    MACRO_FAT_EPS = int(os.getenv("MEAL_MACRO_FAT_EPS_G", "10"))

    def _name(m: Dict[str, Any]) -> str:
        return str((m or {}).get("foodName") or "").strip()

    def _has_kw(name: str, kws: List[str]) -> bool:
        s = name or ""
        return any(k in s for k in kws)

    def _is_rice(name: str) -> bool:
        return "밥" in (name or "")

    def _is_noodle_or_porridge(name: str) -> bool:
        return _has_kw(name, NOODLE_PORRIDGE_KW)

    def _is_staple(name: str) -> bool:
        return _has_kw(name, STAPLE_KW)

    def _is_soup(name: str) -> bool:
        return _has_kw(name, SOUP_KW)

    def _totals(meals: List[Dict[str, Any]]) -> Dict[str, int]:
        return {
            "cal": sum(int(m.get("calories", 0) or 0) for m in meals),
            "carb": sum(int(m.get("carbs", 0) or 0) for m in meals),
            "prot": sum(int(m.get("protein", 0) or 0) for m in meals),
            "fat": sum(int(m.get("fat", 0) or 0) for m in meals),
        }

    def _meal_has_kw(meals: List[Dict[str, Any]], meal_time: str, kws: List[str]) -> bool:
        for m in meals:
            if str(m.get("mealTime") or "").upper() != meal_time:
                continue
            if _has_kw(_name(m), kws):
                return True
        return False

    def _meal_has_staple(meals: List[Dict[str, Any]], meal_time: str) -> bool:
        return _meal_has_kw(meals, meal_time, STAPLE_KW)

    def _meal_has_soup(meals: List[Dict[str, Any]], meal_time: str) -> bool:
        return _meal_has_kw(meals, meal_time, SOUP_KW)

    def _meal_has_rice(meals: List[Dict[str, Any]], meal_time: str) -> bool:
        for m in meals:
            if str(m.get("mealTime") or "").upper() != meal_time:
                continue
            if _is_rice(_name(m)):
                return True
        return False

    def _append_item_to_meal(item: Dict[str, Any], meal_time: str) -> None:
        day_meals.append(
            {
                "mealDate": day_meals[0]["mealDate"],
                "mealTime": meal_time,
                "status": "PLANNED",
                "isAdditional": False,
                "foodName": item.get("foodName") or "알 수 없음",
                "servingSize": item.get("servingSize") or "1인분",
                "calories": int(item.get("calories", 0) or 0),
                "carbs": int(item.get("carbs", 0) or 0),
                "protein": int(item.get("protein", 0) or 0),
                "fat": int(item.get("fat", 0) or 0),
                "originalFoodName": item.get("originalFoodName") or (item.get("foodName") or "알 수 없음"),
                "originalServingSize": item.get("originalServingSize") or (item.get("servingSize") or "1인분"),
                "originalCalories": int(item.get("originalCalories", item.get("calories", 0) or 0) or 0),
                "originalCarbs": int(item.get("originalCarbs", item.get("carbs", 0) or 0) or 0),
                "originalProtein": int(item.get("originalProtein", item.get("protein", 0) or 0) or 0),
                "originalFat": int(item.get("originalFat", item.get("fat", 0) or 0) or 0),
            }
        )

    def _place_items(items: List[Dict[str, Any]], *, allow_second_staple_by_carb_deficit: int) -> None:
        # 끼니별 현재 칼로리
        meal_cals = {"BREAKFAST": 0, "LUNCH": 0, "DINNER": 0}
        for m in day_meals:
            mt = str(m.get("mealTime") or "DINNER").upper()
            if mt in meal_cals:
                meal_cals[mt] += int(m.get("calories", 0) or 0)

        for it in items or []:
            nm = str(it.get("foodName") or "").strip()
            is_soup = _is_soup(nm)
            is_staple = _is_staple(nm)
            is_np = _is_noodle_or_porridge(nm)

            # 낮은 칼로리 끼니부터 시도
            for mt in sorted(meal_cals.keys(), key=lambda k: meal_cals[k]):
                # 국물 1개 제한
                if is_soup and _meal_has_soup(day_meals, mt):
                    continue

                # 주식 기본 1개 제한
                if is_staple and _meal_has_staple(day_meals, mt):
                    # 예외: 탄수 크게 부족 + 해당 끼니에 '밥'이 있고 + 추가하려는 것이 '죽/면'
                    if not (
                        allow_second_staple_by_carb_deficit >= CARB_DEFICIT_ALLOW_SECOND_STAPLE
                        and _meal_has_rice(day_meals, mt)
                        and is_np
                    ):
                        continue

                _append_item_to_meal(it, mt)
                meal_cals[mt] += int(it.get("calories", 0) or 0)
                break

    # ---------------------------
    # 목표/현재
    # ---------------------------
    tcal = int(target.get("targetCalories", 0) or 0)
    tcarb = int(target.get("targetCarbs", 0) or 0)
    tprot = int(target.get("targetProtein", 0) or 0)
    tfat = int(target.get("targetFat", 0) or 0)

    if tcal <= 0:
        return

    cal_threshold = max(200, int(tcal * 0.1))

    def _recalc_need() -> Tuple[int, int, int, int, int]:
        tot = _totals(day_meals)
        need_cal = tcal - tot["cal"]  # 음수면 과다
        need_carb = tcarb - tot["carb"]
        need_prot = tprot - tot["prot"]
        need_fat = tfat - tot["fat"]
        return need_cal, need_carb, need_prot, need_fat, tot["cal"]

    # ---------------------------
    # 1차: 칼로리 맞추기 (추가/제거)
    # ---------------------------
    need_cal, need_carb, need_prot, need_fat, cur_cal = _recalc_need()

    if need_cal > cal_threshold:
        # 칼로리만 먼저 채움 (죽/면/국물은 전면 금지하지 않음)
        current_names = [_name(m) for m in day_meals if _name(m)]
        fillers = pick_foods_for_macros(
            {"targetCalories": need_cal, "targetCarbs": 0, "targetProtein": 0, "targetFat": 0},
            exclude_keywords=[],
            exclude_food_names=current_names,
            min_items=1,
            max_items=3,
            seed=rng.randint(0, 10000),
        )
        _place_items(fillers, allow_second_staple_by_carb_deficit=max(0, need_carb))

    elif need_cal < -cal_threshold:
        # 과다: 밥/국물류 보존하고, 나머지에서 먼저 제거
        to_remove = abs(need_cal)

        # 제거 후보: 보존 키워드 없고, additional이 아닌 것 우선
        candidates: List[int] = []
        for i, m in enumerate(day_meals):
            nm = _name(m)
            if not nm:
                continue
            if _has_kw(nm, PRESERVE_KW):
                continue
            candidates.append(i)

        # 매크로 과다일수록 해당 매크로가 큰 음식 먼저 제거하도록 점수화
        excess_prot = max(0, -need_prot)
        excess_fat = max(0, -need_fat)
        deficit_carb = max(0, need_carb)

        def _remove_score(idx: int) -> float:
            m = day_meals[idx]
            cal = int(m.get("calories", 0) or 0)
            carb = int(m.get("carbs", 0) or 0)
            prot = int(m.get("protein", 0) or 0)
            fat = int(m.get("fat", 0) or 0)
            # 단/지 과다 줄이기 우선, 탄수는 부족하면 덜 빼도록 페널티
            score = cal * 1.0
            if excess_prot > MACRO_PROT_EPS:
                score += prot * 6.0
            if excess_fat > MACRO_FAT_EPS:
                score += fat * 9.0
            if deficit_carb > MACRO_CARB_EPS:
                score -= carb * 4.0
            return score

        candidates.sort(key=_remove_score, reverse=True)

        removed_cal = 0
        removed_indices: List[int] = []
        # 너무 과하게 줄이지 않도록 (target - 200kcal 아래로는 잘 안 내려가게)
        safe_floor = tcal - 200

        for idx in candidates:
            if removed_cal >= to_remove:
                break
            cal = int(day_meals[idx].get("calories", 0) or 0)
            if (cur_cal - (removed_cal + cal)) < safe_floor:
                continue
            removed_indices.append(idx)
            removed_cal += cal

        for idx in sorted(removed_indices, reverse=True):
            day_meals.pop(idx)

    # ---------------------------
    # 2차: 탄/단/지 미세조정
    # ---------------------------
    need_cal, need_carb, need_prot, need_fat, cur_cal = _recalc_need()

    # (A) 탄수 크게 부족 + 단/지 과다이면: 먼저 단/지 많은 항목을 조금 빼서 자리 만든 다음 탄수 채움
    if need_carb > MACRO_CARB_EPS and ((-need_prot) > MACRO_PROT_EPS or (-need_fat) > MACRO_FAT_EPS):
        # 제거 후보: 보존 키워드 제외
        candidates = []
        for i, m in enumerate(day_meals):
            nm = _name(m)
            if not nm or _has_kw(nm, PRESERVE_KW):
                continue
            candidates.append(i)

        def _macro_trim_score(idx: int) -> float:
            m = day_meals[idx]
            carb = int(m.get("carbs", 0) or 0)
            prot = int(m.get("protein", 0) or 0)
            fat = int(m.get("fat", 0) or 0)
            cal = int(m.get("calories", 0) or 0)
            # 단/지 많은 것 우선 제거, 탄수 많은 건 보존
            return (prot * 6.0 + fat * 9.0 + cal * 0.5) - carb * 4.0

        candidates.sort(key=_macro_trim_score, reverse=True)

        # 200~600kcal 정도만 우선 트림 (과도한 변형 방지)
        trim_cap = int(os.getenv("MEAL_MACRO_TRIM_CAP_KCAL", "450"))
        removed = 0
        safe_floor = tcal - 250
        for idx in candidates:
            if removed >= trim_cap:
                break
            cal = int(day_meals[idx].get("calories", 0) or 0)
            if (_totals(day_meals)["cal"] - (removed + cal)) < safe_floor:
                continue
            removed += cal
            day_meals.pop(idx)
            # 인덱스가 꼬이므로 pop 후 즉시 break하고 재계산
            break

        need_cal, need_carb, need_prot, need_fat, cur_cal = _recalc_need()

    # (B) 남은 부족분을 매크로 기반으로 채움 (필요시 2번 시도)
    for _ in range(2):
        need_cal, need_carb, need_prot, need_fat, cur_cal = _recalc_need()
        if need_cal <= 0 and need_carb <= MACRO_CARB_EPS and need_prot <= MACRO_PROT_EPS and need_fat <= MACRO_FAT_EPS:
            break

        # 칼로리는 이미 1차에서 맞췄으므로, 2차는 매크로 중심.
        # 다만 칼로리 헤드룸이 거의 없으면, 무조건 추가하지 않음.
        cal_headroom = max(0, tcal - cur_cal)
        if cal_headroom < 120 and need_carb <= MACRO_CARB_EPS:
            break

        # 목표치: 매크로 부족분 + 남은 칼로리(헤드룸) 정도만 반영
        target2 = {
            "targetCalories": max(0, min(cal_headroom, max(200, int(need_carb * 4)))),  # 탄수 부족이면 탄수 기준 kcal
            "targetCarbs": max(0, need_carb),
            "targetProtein": max(0, need_prot),
            "targetFat": max(0, need_fat),
        }

        current_names = [_name(m) for m in day_meals if _name(m)]
        fillers2 = pick_foods_for_macros(
            target2,
            exclude_keywords=[],
            exclude_food_names=current_names,
            min_items=1,
            max_items=2,
            seed=rng.randint(0, 10000),
        )
        _place_items(fillers2, allow_second_staple_by_carb_deficit=max(0, need_carb))


def replan_meal_plan(goal: Dict[str, Any], current_meals: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    [RAG-first Replan]
    - LLM-first(메뉴명 생성) 대신 meal_templates(한 끼 템플릿) 기반으로 남은 끼니를 재구성합니다.
    - goal은 "남은 목표치"로 전달되므로, 남은 끼니 수에 맞춰 템플릿 조합을 선택합니다.
    - 템플릿이 없으면 기존 LLM replan 프롬프트로 안전하게 fallback 합니다.
    """
    target = {
        "targetCalories": int(round(float((goal or {}).get("targetCalories", 0) or 0))),
        "targetCarbs": int(round(float((goal or {}).get("targetCarbs", 0) or 0))),
        "targetProtein": int(round(float((goal or {}).get("targetProtein", 0) or 0))),
        "targetFat": int(round(float((goal or {}).get("targetFat", 0) or 0))),
    }

    # "남은 끼니"는 사용자 관점에서 "아직 완료처리 안 된 끼니(= non-additional PLANNED가 남아있는 끼니)"입니다.
    # 기존 로직(어떤 항목이라도 EATEN/SKIPPED가 있으면 done 처리)은
    # 같은 끼니 안에 SKIPPED가 섞여 있을 때(예: 일부 항목 SKIPPED) 남은 끼니가 전부 done으로 판단되어
    # 재분배가 아예 안 되는 문제를 만들었습니다.
    planned_times: set[str] = set()
    for m in current_meals or []:
        try:
            mt = str((m or {}).get("mealTime") or "").upper().strip()
            st = str((m or {}).get("status") or "").upper().strip()
            is_add = bool((m or {}).get("isAdditional") or False)
        except Exception:
            continue
        if mt not in ("BREAKFAST", "LUNCH", "DINNER"):
            continue
        if st == "PLANNED" and not is_add:
            planned_times.add(mt)

    remaining_times = [t for t in ("BREAKFAST", "LUNCH", "DINNER") if t in planned_times]
    if not remaining_times:
        return {"suggestedMeals": []}

    templates = _load_templates_from_qdrant()
    if not templates:
        # 템플릿이 아직 없으면 기존 LLM replan으로 fallback (서비스 중단 방지)
        raw = call_meal_ai_json(
            system_prompt=REPLAN_SYSTEM_PROMPT,
            user_prompt=get_replan_prompt(goal, current_meals),
            temperature=0.3,
        )
        meals = raw.get("meals") or []
        suggested = []
        for m in meals:
            meal_time = (m.get("mealTime") or "DINNER").upper()
            food_name = m.get("foodName") or "알 수 없음"
            resolved_name, macros, db_serving = lookup_food_details(food_name, extra_queries=[])
            serving = db_serving or (m.get("servingSize") or "1인분")
            suggested.append(
                {
                    "mealTime": meal_time,
                    "status": "PLANNED",
                    "isAdditional": False,
                    "foodName": resolved_name,
                    "servingSize": serving,
                    **macros,
                    "originalFoodName": resolved_name,
                    "originalServingSize": serving,
                    "originalCalories": macros["calories"],
                    "originalCarbs": macros["carbs"],
                    "originalProtein": macros["protein"],
                    "originalFat": macros["fat"],
                }
            )
        return {"suggestedMeals": suggested}

    rng = random.Random()
    rng.seed(
        hash(
            (
                target.get("targetCalories", 0),
                target.get("targetProtein", 0),
                ",".join(remaining_times),
            )
        )
    )

    chosen: List[Dict[str, Any]] = []
    n = len(remaining_times)

    if n >= 3:
        # 3끼 모두 남아 있으면 기존 day-template 조합 로직을 재사용
        picked = _pick_day_templates(
            templates,
            target,
            rng=rng,
            cooldown_days=0,
            last_used_day={},
            day_index=0,
        )
        chosen = picked[:3]
    elif n == 2:
        per_meal_cal = max(1, int(target.get("targetCalories", 0) / 2)) if target.get("targetCalories") else 500
        eligible_sorted = sorted(templates, key=lambda t: abs(t["totals"]["calories"] - per_meal_cal))
        pool = eligible_sorted[: min(80, len(eligible_sorted))]
        if len(pool) < 2:
            pool = eligible_sorted

        best_pair = None
        best_score = float("inf")
        trials = min(5000, max(200, len(pool) * 60))
        for _ in range(trials):
            a, b = rng.sample(pool, 2)
            if a.get("templateId") == b.get("templateId"):
                continue
            totals = _combine_totals(a.get("totals") or {}, b.get("totals") or {})
            sc = _score_totals(totals, target)
            if sc < best_score:
                best_score = sc
                best_pair = (a, b)
        if best_pair:
            chosen = [best_pair[0], best_pair[1]]
        else:
            chosen = rng.sample(pool, 2)
    else:
        # n == 1
        chosen = [min(templates, key=lambda t: _score_totals(t.get("totals") or {}, target))]

    suggested: List[Dict[str, Any]] = []
    for meal_time, tpl in zip(remaining_times, chosen):
        items = (tpl or {}).get("items") or []
        for it in items:
            food_name = str((it or {}).get("foodName") or "알 수 없음").strip()
            macros = {
                "calories": int(round(float((it or {}).get("calories", 0) or 0))),
                "carbs": int(round(float((it or {}).get("carbs", 0) or 0))),
                "protein": int(round(float((it or {}).get("protein", 0) or 0))),
                "fat": int(round(float((it or {}).get("fat", 0) or 0))),
            }
            serving = "1인분"
            suggested.append(
                {
                    "mealTime": meal_time,
                    "status": "PLANNED",
                    "isAdditional": False,
                    "foodName": food_name,
                    "servingSize": serving,
                    **macros,
                    "originalFoodName": food_name,
                    "originalServingSize": serving,
                    "originalCalories": macros["calories"],
                    "originalCarbs": macros["carbs"],
                    "originalProtein": macros["protein"],
                    "originalFat": macros["fat"],
                }
            )

    return {"suggestedMeals": suggested}


def pick_foods_for_macros(
    target: Dict[str, Any],
    *,
    exclude_keywords: Optional[List[str]] = None,
    exclude_food_names: Optional[List[str]] = None,
    min_items: int = 1,
    max_items: int = 3,
    seed: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """
    목표 매크로(칼로리/탄/단/지)에 맞춰 meal_foods(개별 음식 풀)에서 1~3개 음식을 골라줍니다.
    - 템플릿(meal_templates) 복제가 아니라 개별 음식을 선택합니다.
    - exclude_keywords: 이름에 포함되면 제외 (예: ["밥","국","찌개"])
    - exclude_food_names: 생략된 끼니 메뉴 등 제외할 음식명 목록
    """
    t = {
        "targetCalories": int(round(float((target or {}).get("targetCalories", 0) or 0))),
        "targetCarbs": int(round(float((target or {}).get("targetCarbs", 0) or 0))),
        "targetProtein": int(round(float((target or {}).get("targetProtein", 0) or 0))),
        "targetFat": int(round(float((target or {}).get("targetFat", 0) or 0))),
    }

    k_min = max(1, min(3, int(min_items or 1)))
    k_max = max(k_min, min(3, int(max_items or 3)))

    ex_kw = [str(x).strip() for x in (exclude_keywords or []) if str(x).strip()]
    ex_names_norm = {_normalize_food_name(str(x)) for x in (exclude_food_names or []) if str(x).strip()}

    foods = _load_food_cache()

    def _is_excluded(item: Dict[str, Any]) -> bool:
        name = str(item.get("displayName") or item.get("foodName") or "").strip()
        if not name:
            return True
        norm = item.get("foodNameNormalized") or _normalize_food_name(name)
        if norm and norm in ex_names_norm:
            return True
        for kw in ex_kw:
            if kw and kw in name:
                return True
        # 영양 정보가 0인 항목은 제외 (매칭 품질 향상)
        cal = int(item.get("calories", 0) or 0)
        if cal <= 0:
            return True
        return False

    candidates = [f for f in foods if not _is_excluded(f)]
    if not candidates:
        return []

    rng = random.Random(seed or hash((t["targetCalories"], t["targetProtein"], len(candidates))))

    def _totals(items: List[Dict[str, Any]]) -> Dict[str, int]:
        tot = {"calories": 0, "carbs": 0, "protein": 0, "fat": 0}
        for it in items:
            tot["calories"] += int(it.get("calories", 0) or 0)
            tot["carbs"] += int(it.get("carbs", 0) or 0)
            tot["protein"] += int(it.get("protein", 0) or 0)
            tot["fat"] += int(it.get("fat", 0) or 0)
        return tot

    best_combo: Optional[List[Dict[str, Any]]] = None
    best_score = float("inf")

    for k in range(k_min, k_max + 1):
        per_cal = max(1, int(t["targetCalories"] / k)) if t["targetCalories"] else 500
        pool = sorted(candidates, key=lambda f: abs(int(f.get("calories", 0) or 0) - per_cal))
        pool = pool[: min(300, len(pool))]
        if len(pool) < k:
            pool = candidates[:]

        trials = min(6000, max(400, len(pool) * 40))
        if k == 1:
            for it in pool[: min(len(pool), 800)]:
                sc = _score_totals(_totals([it]), t)
                if sc < best_score:
                    best_score = sc
                    best_combo = [it]
            continue

        if k == 2:
            for _ in range(trials):
                a, b = rng.sample(pool, 2)
                sc = _score_totals(_totals([a, b]), t)
                if sc < best_score:
                    best_score = sc
                    best_combo = [a, b]
            continue

        # k == 3
        for _ in range(trials):
            a, b, c = rng.sample(pool, 3)
            sc = _score_totals(_totals([a, b, c]), t)
            if sc < best_score:
                best_score = sc
                best_combo = [a, b, c]

    if not best_combo:
        best_combo = [min(candidates, key=lambda f: _score_totals(_totals([f]), t))]

    picked: List[Dict[str, Any]] = []
    for it in best_combo:
        name = str(it.get("displayName") or it.get("foodName") or "알 수 없음").strip()
        serving = str(it.get("servingSize") or "1인분").strip() or "1인분"
        cal = int(it.get("calories", 0) or 0)
        carbs = int(it.get("carbs", 0) or 0)
        protein = int(it.get("protein", 0) or 0)
        fat = int(it.get("fat", 0) or 0)
        picked.append(
            {
                "foodName": name,
                "servingSize": serving,
                "calories": cal,
                "carbs": carbs,
                "protein": protein,
                "fat": fat,
                # original_*은 Java DTO 매핑/분석 UI 일관성 목적
                "originalFoodName": name,
                "originalServingSize": serving,
                "originalCalories": cal,
                "originalCarbs": carbs,
                "originalProtein": protein,
                "originalFat": fat,
            }
        )

    return picked


def generate_meal_advice(current_meals: List[Dict[str, Any]], user_question: Optional[str] = None) -> Dict[str, Any]:
    raw = call_meal_ai_json(
        system_prompt=ADVICE_SYSTEM_PROMPT,
        user_prompt=get_advice_prompt(current_meals, user_question),
        temperature=0.4,
    )
    comment = raw.get("adviceComment") or ""
    return {"adviceComment": comment.strip()}


