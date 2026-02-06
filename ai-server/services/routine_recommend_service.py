"""
RAG 기반 루틴/대체 운동 추천 서비스
- 타겟 부위 유지 + 부상 위험 부위 배제 (타겟 ∩ 위험배제) 로직
- 2/4/5 분할 정의 지원
"""
import json
from pathlib import Path
from typing import List, Dict, Any, Optional

# 프로젝트 루트의 rag_data.json 로드
def _load_rag_data() -> List[Dict[str, Any]]:
    path = Path(__file__).resolve().parent.parent.parent / "rag_data.json"
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return []
    except json.JSONDecodeError:
        return []


def get_split_definitions() -> Dict[str, List[Dict[str, Any]]]:
    """2/4/5 분할 정의 반환. { "split_2": [...], "split_4": [...], "split_5": [...] }"""
    data = _load_rag_data()
    for item in data:
        if item.get("category") == "split_definitions":
            return {
                "split_2": item.get("split_2", []),
                "split_4": item.get("split_4", []),
                "split_5": item.get("split_5", []),
            }
    # 폴백: 하드코딩
    return {
        "split_2": [
            {"name": "Upper", "body_parts": ["가슴", "등", "어깨", "팔", "코어", "복근"]},
            {"name": "Leg", "body_parts": ["허벅지", "둔근", "종아리"]},
        ],
        "split_4": [
            {"name": "가슴삼두", "body_parts": ["가슴", "팔"]},
            {"name": "등이두", "body_parts": ["등", "팔"]},
            {"name": "어깨", "body_parts": ["어깨"]},
            {"name": "하체", "body_parts": ["허벅지", "둔근", "종아리", "코어", "복근"]},
        ],
        "split_5": [
            {"name": "가슴", "body_parts": ["가슴"]},
            {"name": "등", "body_parts": ["등"]},
            {"name": "어깨", "body_parts": ["어깨"]},
            {"name": "팔", "body_parts": ["팔"]},
            {"name": "하체", "body_parts": ["허벅지", "둔근", "종아리", "코어", "복근"]},
        ],
    }


def _exercise_routine_items() -> List[Dict[str, Any]]:
    """exercise_routine 카테고리 항목만 반환"""
    data = _load_rag_data()
    return [x for x in data if x.get("category") == "exercise_routine"]


def _injury_items() -> List[Dict[str, Any]]:
    """exercise_injury 카테고리 항목만 반환"""
    data = _load_rag_data()
    return [x for x in data if x.get("category") == "exercise_injury"]


def _risk_factors_for_exercise(exercise_name: str) -> List[str]:
    """운동명에 대한 risk_factors (부상 위험 부위) 반환"""
    routines = _exercise_routine_items()
    for r in routines:
        if r.get("exercise_name") == exercise_name:
            return r.get("risk_factors") or []
    injuries = _injury_items()
    for i in injuries:
        if i.get("exercise_name") == exercise_name:
            return i.get("injury_risks") or []
    return []


def recommend_exercises(
    target_body_parts: List[str],
    exclude_body_parts: List[str],
    limit: int = 10,
    exclude_exercise_names: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """
    RAG exercise_routine만 사용. 해당 일의 타겟 부위와 주 타겟(body_part)이 일치하는 운동만 추천.
    - target_body_parts: 이번 날의 타겟 부위 (예: 4분할 1일차 = ["가슴", "팔"])
    - exclude_body_parts: 부상 위험으로 제외할 부위
    - exclude_exercise_names: 이미 다른 일차에서 쓴 운동명 (중복 제거)
    - 조건: body_part가 target_body_parts에 포함될 때만 포함 (tags 보조 타겟으로 넣지 않음)
    """
    if not target_body_parts:
        return []
    exclude_set = set(exclude_body_parts) if exclude_body_parts else set()
    exclude_names_set = set(exclude_exercise_names or [])
    target_set = set(target_body_parts)
    items = _exercise_routine_items()
    result = []
    for item in items:
        name = item.get("exercise_name") or ""
        if exclude_names_set and name in exclude_names_set:
            continue
        bp = (item.get("body_part") or "").strip()
        if not bp or bp not in target_set:
            continue
        risk = item.get("risk_factors") or []
        if exclude_set and set(risk) & exclude_set:
            continue
        result.append({
            "id": item.get("id"),
            "exercise_name": item.get("exercise_name"),
            "body_part": bp,
            "title": item.get("title"),
            "risk_factors": risk,
        })
        if len(result) >= limit:
            break
    return result


def recommend_for_split_day(
    split_type: int,
    day_index: int,
    exclude_body_parts: List[str],
    limit: int = 10,
    exclude_exercise_names: Optional[List[str]] = None,
) -> List[Dict[str, Any]]:
    """
    분할 타입(2/4/5)과 요일 인덱스에 해당하는 부위로 추천.
    day_index는 0-based (0=첫 번째 날).
    exclude_exercise_names: 이전 일차에서 이미 추천된 운동명 (중복 제거).
    """
    splits = get_split_definitions()
    key = f"split_{split_type}"
    days = splits.get(key) or splits.get("split_2")
    if day_index < 0 or day_index >= len(days):
        return []
    target_body_parts = days[day_index].get("body_parts") or []
    return recommend_exercises(
        target_body_parts=target_body_parts,
        exclude_body_parts=exclude_body_parts,
        limit=limit,
        exclude_exercise_names=exclude_exercise_names,
    )


def get_alternatives_for_exercise(
    exercise_name: str,
    exclude_body_parts: Optional[List[str]] = None,
) -> Dict[str, Any]:
    """
    특정 운동의 대체 운동 추천 (exercise_injury 기반).
    exclude_body_parts가 있으면 해당 부위에 부담을 주지 않는 대체만 반환.
    has_risk_for_excluded_area: 통증 부위가 이 운동의 부상위험부위에 포함될 때만 True (모달에 변경 노출할지 판단용).
    """
    exclude_set = set(exclude_body_parts or [])
    risk_factors = _risk_factors_for_exercise(exercise_name)
    injury_list = _injury_items()
    injury_risks = []
    alts = []
    for inv in injury_list:
        if inv.get("exercise_name") != exercise_name:
            continue
        injury_risks = inv.get("injury_risks") or []
        alts = inv.get("alternatives") or []
        break
    risk_set = set(risk_factors) | set(injury_risks)
    has_risk_for_excluded_area = bool(exclude_set and (risk_set & exclude_set))

    routine_items = {r["exercise_name"]: r for r in _exercise_routine_items()}
    filtered = []
    for alt in alts:
        risk = routine_items.get(alt, {}).get("risk_factors") or _risk_factors_for_exercise(alt)
        if exclude_set and set(risk) & exclude_set:
            continue
        filtered.append(alt)

    return {
        "exercise_name": exercise_name,
        "injury_risks": injury_risks,
        "alternatives": filtered if filtered else alts,
        "has_risk_for_excluded_area": has_risk_for_excluded_area,
    }
