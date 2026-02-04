"""Commerce 추천 조건 생성 (RAG 정책 문서 + LLM)."""
import time
from typing import Dict, Any, Optional, List

from services.ai_service import call_ai_json
from services.backend_client import get_user_profile
from .commerce_rag_service import search_commerce_rag, COMMERCE_POLICY_DOC_TYPES
from prompts.commerce.commerce_recommendation import build_system_prompt, build_user_prompt
from schemas.commerce.recommendation_schema import RecommendationCondition


_KEYWORD_SYNONYMS = {
    # 간단한 동의어/표현 통일: 카탈로그 검색 적합한 키워드로 정규화
    "아령": "덤벨",
    "아령세트": "덤벨",
    "아령 세트": "덤벨",
    "덤벨세트": "덤벨",
    "덤벨 세트": "덤벨",
    "손목스트랩": "리프팅 스트랩",
    "손목 스트랩": "리프팅 스트랩",
    "스트랩": "리프팅 스트랩",
    "리스트랩": "리프팅 스트랩",
    "손목밴드": "손목 밴드",
    "손목 밴드": "손목 밴드",
    "무릎보호대": "무릎 보호대",
}

# 부위 키워드 (백엔드 extractBodyTokens와 일치)
_BODY_PART_TOKENS = frozenset({
    "무릎", "하체", "허벅지", "손목", "손", "허리", "등", "어깨", "발목", "팔꿈치", "상체", "팔"
})

# 상품 유형 키워드 (must_have 후보)
_PRODUCT_TYPE_TOKENS = frozenset({
    "보호대", "니슬리브", "니랩", "스트랩", "밴드", "벨트", "슬리브",
    "덤벨", "바벨", "케틀벨", "기구", "매트", "폼롤러", "풀업바",
    "보충제", "프로틴", "아미노산", "크레아틴", "bcaa",
    "음식", "식품", "국수", "닭가슴살", "샐러드", "간식",
    "레깅스", "운동복", "반바지", "티셔츠"
})

# 영양/성분/효과 키워드 (priority 후보)
_NUTRITION_EFFECT_TOKENS = frozenset({
    "단백질", "식이섬유", "칼로리", "저칼로리", "고단백", "저탄수화물",
    "게이너", "증량", "다이어트", "체중감량", "근육", "근성장", "벌크업",
    "유지", "회복", "에너지", "지구력", "체력"
})


def _classify_keywords(keywords: List[str]) -> Dict[str, List[str]]:
    """
    키워드를 부위(body_parts), 상품유형(type_must), 영양/효과(priority)로 분류한다.
    - 부위 키워드 → body_parts (백엔드에서 점수 보정용으로 사용)
    - 상품 유형 키워드 → type_must (must_have 후보)
    - 영양/성분/효과 키워드 → priority (soft preference)
    """
    body_parts: List[str] = []
    type_must: List[str] = []
    priority_kw: List[str] = []
    
    for kw in keywords:
        if not kw or not isinstance(kw, str):
            continue
        kw_lower = kw.strip().lower()
        if not kw_lower:
            continue
        
        is_body = any(bp in kw_lower for bp in _BODY_PART_TOKENS)
        is_type = any(tp in kw_lower for tp in _PRODUCT_TYPE_TOKENS)
        is_nutrition = any(nt in kw_lower for nt in _NUTRITION_EFFECT_TOKENS)
        
        # 분류 우선순위: 상품 유형 > 영양/효과 > 부위
        if is_type:
            type_must.append(kw.strip())
        elif is_nutrition:
            priority_kw.append(kw.strip())
        elif is_body:
            body_parts.append(kw.strip())
        else:
            # 분류 안 되면 type_must에 추가 (상품 검색 시 사용)
            type_must.append(kw.strip())
    
    return {
        "body_parts": body_parts,
        "type_must": type_must,
        "priority": priority_kw,
    }


def _normalize_keyword_for_catalog(raw: Optional[str]) -> Optional[str]:
    """카탈로그 검색에 더 잘 맞도록 keyword를 간단히 정규화."""
    if not raw or not isinstance(raw, str):
        return raw
    k = raw.strip()
    if not k:
        return None
    # 완전 일치 동의어 매핑
    if k in _KEYWORD_SYNONYMS:
        return _KEYWORD_SYNONYMS[k]
    # 공백 제거 버전도 한 번 더 시도
    k_no_space = k.replace(" ", "")
    if k_no_space in _KEYWORD_SYNONYMS:
        return _KEYWORD_SYNONYMS[k_no_space]
    return k


def generate_recommendation_condition(
    user_text: str,
    extracted_slots: Dict[str, Any],
    auth_token: Optional[str] = None,
    profile_context: Optional[Dict[str, Any]] = None,
) -> Optional[RecommendationCondition]:
    t0 = time.time()
    try:
        user_profile: Optional[Dict[str, Any]] = None
        if profile_context is not None:
            user_profile = {
                "goal": profile_context.get("goal_type"),
                "heightCm": profile_context.get("member_height_cm"),
                "weightKg": profile_context.get("member_weight_kg"),
                "budgetMax": profile_context.get("budget_max") or profile_context.get("budgetMax"),
                "avoid": list(profile_context.get("profile_avoid") or []),
                "gender": profile_context.get("member_gender"),
            }
            print(f"[commerce] recommend_condition_start using_session_profile total={time.time() - t0:.2f}s")
        elif auth_token:
            t_profile_start = time.time()
            user_profile = get_user_profile(auth_token)
            print(
                f"[commerce] profile_fetch done elapsed={time.time() - t_profile_start:.2f}s "
                f"total={time.time() - t0:.2f}s"
            )
        else:
            print(f"[commerce] recommend_condition_start (no profile) total={time.time() - t0:.2f}s")

        # 슬롯 기반 keyword/core_keywords/negative_keywords를 카탈로그 친화적으로 한 번 정규화
        slots = dict(extracted_slots or {})
        if "keyword" in slots:
            slots["keyword"] = _normalize_keyword_for_catalog(slots.get("keyword"))
        core_kw_raw = slots.get("core_keywords") or []
        neg_kw_raw = slots.get("negative_keywords") or []
        core_keywords: List[str] = []
        negative_keywords: List[str] = []
        if isinstance(core_kw_raw, list):
            core_keywords = [str(x).strip() for x in core_kw_raw if x is not None and str(x).strip()]
        if isinstance(neg_kw_raw, list):
            negative_keywords = [str(x).strip() for x in neg_kw_raw if x is not None and str(x).strip()]

        t_rag_start = time.time()
        goal = slots.get("goal", "ALL")
        product_category = slots.get("product_category", "ALL")
        doc_types = COMMERCE_POLICY_DOC_TYPES

        rag_out = search_commerce_rag(
            goal=goal if goal != "ALL" else None,
            product_category=product_category if product_category != "ALL" else None,
            doc_types=doc_types,
            query_text=user_text,
            limit=3,
        )
        rag_results = rag_out.get("results") or []
        rag_hit = rag_out.get("rag_hit", False)
        retrieved_chunks = rag_out.get("retrieved_chunks", 0)
        rag_error = rag_out.get("error")

        if not rag_hit:
            print(f"[commerce] rag_done rag_hit=false retrieved_chunks=0 error={rag_error!r} elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")
        else:
            print(f"[commerce] rag_done rag_hit=true retrieved_chunks={retrieved_chunks} elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")

        top_results = sorted(
            rag_results,
            key=lambda r: r.get("score", 0.0),
            reverse=True,
        )

        rag_context_parts = []
        for result in top_results:
            if result.get("title"):
                rag_context_parts.append(f"## {result['title']}")
            if result.get("content"):
                content = str(result["content"])
                if len(content) > 500:
                    content = content[:500] + " ..."
                rag_context_parts.append(content)
            if result.get("section"):
                rag_context_parts.append(f"\n섹션: {result['section']}")
            rag_context_parts.append("")

        if rag_context_parts:
            rag_context = "\n".join(rag_context_parts)
        else:
            rag_context = "추천 규칙 정보가 없습니다. (RAG 검색 미사용 또는 결과 없음)"
        print(f"[commerce] rag_context built elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")

        system_prompt = build_system_prompt(rag_context, user_profile or {})
        user_prompt = build_user_prompt(user_text, slots)

        t_llm_start = time.time()
        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            temperature=0.3
        )
        print(f"[commerce] llm_done elapsed={time.time() - t_llm_start:.2f}s total={time.time() - t0:.2f}s")

        condition = RecommendationCondition.from_dict(result)

        # core_keywords를 부위/상품유형/영양으로 분류하여 적절한 필드에 반영
        try:
            if core_keywords:
                classified = _classify_keywords(core_keywords)
                
                # 상품 유형 키워드 → must_have
                base_must = list(condition.must_have or [])
                base_lower = {m.lower() for m in base_must}
                for term in classified["type_must"]:
                    if term.lower() not in base_lower:
                        base_must.append(term)
                        base_lower.add(term.lower())
                condition.must_have = base_must
                
                # 영양/효과 키워드 → priority
                base_priority = list(condition.priority or [])
                base_priority_lower = {p.lower() for p in base_priority}
                for term in classified["priority"]:
                    if term.lower() not in base_priority_lower:
                        base_priority.append(term)
                        base_priority_lower.add(term.lower())
                condition.priority = base_priority
                
                # 부위 키워드 → derived_constraints["body_parts"]
                if classified["body_parts"]:
                    if not condition.derived_constraints:
                        condition.derived_constraints = {}
                    existing_body = condition.derived_constraints.get("body_parts") or []
                    existing_lower = {b.lower() for b in existing_body}
                    for bp in classified["body_parts"]:
                        if bp.lower() not in existing_lower:
                            existing_body.append(bp)
                            existing_lower.add(bp.lower())
                    condition.derived_constraints["body_parts"] = existing_body
                
                print(f"[commerce] core_keywords 분류 완료: type_must={classified['type_must']}, "
                      f"priority={classified['priority']}, body_parts={classified['body_parts']}")

            if negative_keywords:
                base_avoid = list(condition.avoid or [])
                base_lower_avoid = {a.lower() for a in base_avoid}
                for term in negative_keywords:
                    t = term.strip()
                    if t and t.lower() not in base_lower_avoid:
                        base_avoid.append(t)
                        base_lower_avoid.add(t.lower())
                condition.avoid = base_avoid
        except Exception as e:
            # 키워드 병합 실패 시에는 무시하고 기존 condition만 사용
            print(f"[commerce] 키워드 분류/병합 실패: {e}")
            pass

        # 추가 도메인 규칙: 카테고리별 must_have/priority 모순 보정
        try:
            protection_tokens = ("보호대", "니슬리브", "니랩", "무릎 보호", "스트랩", "밴드")
            protein_tokens = ("단백질", "프로틴", "게이너")
            nutrition_tokens = ("단백질", "식이섬유", "칼로리", "저칼로리", "고단백", "저탄수화물")

            if condition.product_category == "HEALTH_GOODS":
                # 보호대/헬스용품인데 단백질/보충제 관련 must_have는 의미가 어긋나므로 priority로 이동
                new_must = []
                for m in (condition.must_have or []):
                    if any(t in m.lower() for t in protein_tokens):
                        # must_have에서 제거하고 priority로 이동
                        if m not in (condition.priority or []):
                            condition.priority = list(condition.priority or []) + [m]
                    else:
                        new_must.append(m)
                condition.must_have = new_must
                
            elif condition.product_category == "SUPPLEMENT":
                kw = condition.keyword or ""
                if any(t in kw for t in protection_tokens):
                    # 보호대 관련 키워드인데 카테고리가 SUPPLEMENT면 HEALTH_GOODS로 보정
                    condition.product_category = "HEALTH_GOODS"
                    print(f"[commerce] 카테고리 보정: SUPPLEMENT → HEALTH_GOODS (keyword={kw})")
                    
            elif condition.product_category == "FOOD":
                # 음식 카테고리에서 영양/성분 키워드는 must_have가 아닌 priority로 이동
                new_must = []
                for m in (condition.must_have or []):
                    if any(t in m.lower() for t in nutrition_tokens):
                        # must_have에서 제거하고 priority로 이동
                        if m not in (condition.priority or []):
                            condition.priority = list(condition.priority or []) + [m]
                    else:
                        new_must.append(m)
                condition.must_have = new_must
                
            # 부위 키워드가 must_have에 남아있으면 제거 (derived_constraints로 이미 이동됨)
            body_tokens = _BODY_PART_TOKENS
            filtered_must = [
                m for m in (condition.must_have or [])
                if not any(bp in m.lower() for bp in body_tokens) or 
                   any(tp in m.lower() for tp in _PRODUCT_TYPE_TOKENS)  # 부위+유형 조합은 유지 (예: "무릎 보호대")
            ]
            if len(filtered_must) != len(condition.must_have or []):
                print(f"[commerce] 부위 키워드 must_have에서 제거: {condition.must_have} → {filtered_must}")
            condition.must_have = filtered_must

        except Exception as e:
            # 보정 중 예외가 나더라도 전체 플로우를 막지는 않는다.
            print(f"[commerce] 도메인 보정 중 예외: {e}")
            pass

        if not condition.validate():
            print("추천 조건 유효성 검증 실패")
            return None

        # 최종 조건 요약 로그
        print(f"[commerce] 최종 추천 조건: {condition.to_summary_log()}")
        print(f"[commerce] generate_recommendation_condition 완료: total={time.time() - t0:.2f}s")
        
        return condition

    except Exception as e:
        print(f"추천 조건 생성 실패: {e}")
        return None


def generate_fallback_conditions(
    user_text: str,
    base_condition: RecommendationCondition,
    max_fallbacks: int = 2,
) -> List[RecommendationCondition]:
    """
    기본 추천 조건으로 상품을 찾지 못했을 때, LLM을 사용해 완화된 fallback 조건들을 생성한다.
    - goal, product_category, keyword는 가능한 한 그대로 유지하고,
      예산·must_have·priority 등 soft 조건만 완화하는 쪽으로 유도한다.
    """
    try:
        system_prompt = """
당신은 건강/운동 상품 추천 조건 튜닝 전문가입니다.
주어진 기본 추천 조건으로는 상품 검색 결과가 거의 없었습니다.

[역할]
- 사용자의 원래 의도를 해치지 않는 선에서, 조건을 "조금씩" 완화한 fallback 조건 1~2개를 제안하세요.
- 특히 다음을 지키세요:
  1) goal, product_category는 가능한 한 그대로 유지합니다. (DIET ↔ BULK_UP 같은 극단적인 변경은 금지)
  2) keyword는 가능한 한 그대로 유지하되, 너무 구체적인 수식어만 제거할 수 있습니다.
     예: "3중 보호 무릎 보호대" → "무릎 보호대"
  3) 예산(budget_max)은 소폭 상향하거나(예: 30000 → 40000), 없애는 방향으로만 완화합니다.
  4) must_have / priority가 너무 많다면 일부를 제거하거나 우선순위를 낮추는 식으로 완화할 수 있습니다.

[주의]
- 사용자가 보호대/스트랩/보충제 등 특정 상품 유형을 명확히 말한 경우,
  그 상품 유형을 전혀 다른 카테고리(예: "무릎 보호대" → "프로틴")로 바꾸지 마세요.

[응답 형식]
JSON만 반환하세요:
{
  "fallbacks": [
    {
      "relax_level": 1,
      "reason": "예산 상한을 약간 올려 더 많은 후보를 보기 위함",
      "condition": {
        "goal": "...",
        "product_category": "...",
        "budget_max": ...,
        "avoid": [...],
        "must_have": [...],
        "priority": [...],
        "keyword": "..." or null
      }
    }
  ]
}
""".strip()

        user_prompt = (
            f"사용자 발화: \"{user_text}\"\n\n"
            f"[기본 추천 조건]\n"
            f"{base_condition.to_dict()}\n\n"
            f"위 조건으로는 상품 검색 결과가 거의 없었습니다.\n"
            f"위 규칙에 따라 1~{max_fallbacks}개의 fallback 조건을 생성하세요."
        )

        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            temperature=0.4,
        )
        fallbacks_data = (result or {}).get("fallbacks") or []
        out: List[RecommendationCondition] = []
        for fb in fallbacks_data[:max_fallbacks]:
            cond_dict = (fb or {}).get("condition") or {}
            # 누락된 필드는 기본 조건에서 보충
            merged = {
                **base_condition.to_dict(),
                **cond_dict,
            }
            try:
                cond = RecommendationCondition.from_dict(merged)
                if cond.validate():
                    out.append(cond)
            except Exception:
                continue
        return out
    except Exception as e:
        print(f"fallback 추천 조건 생성 실패: {e}")
        return []
