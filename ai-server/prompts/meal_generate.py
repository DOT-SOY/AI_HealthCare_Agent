"""
식단 생성 프롬프트 (Meal Generate)

원칙:
- 영양 수치(칼로리/탄단지)를 LLM이 "추론"하지 않음 (DB/Qdrant에서 조회)
- LLM은 메뉴 구성/시간대 배치/서빙 사이즈(텍스트)만 결정
- 출력은 반드시 JSON (application/json) 1개 객체
"""

from __future__ import annotations

from typing import Dict, Any


SYSTEM_PROMPT = """식단 코치. 목표/선호 고려해 메뉴 구성.

규칙:
1) 칼로리/탄단지는 추정하지 않음 (DB 조회)
2) 메뉴명 한국어, 브랜드 포함
3) mealTime: BREAKFAST/LUNCH/DINNER/SNACK
4) JSON만 출력
"""


def get_generate_prompt(profile: Dict[str, Any], goal: Dict[str, Any]) -> str:
    meal_count = goal.get("mealCount") or 3
    allergies = profile.get("allergies") or []
    liked = profile.get("likedFoods") or []
    disliked = profile.get("dislikedFoods") or []

    return f"""사용자: {profile.get('age')}세 {profile.get('gender')}, {profile.get('height')}cm/{profile.get('weight')}kg
목표: {goal.get('goalType')}, {meal_count}끼
알레르기: {allergies}
선호: {liked}
비선호: {disliked}

JSON:
{{
  "meals": [
    {{"mealTime": "BREAKFAST|LUNCH|DINNER", "foodName": "메뉴명", "servingSize": "1인분"}}
  ]
}}
"""


def get_generate_week_prompt(profile: Dict[str, Any], goal: Dict[str, Any]) -> str:
    meal_count = goal.get("mealCount") or 3
    allergies = profile.get("allergies") or []
    liked = profile.get("likedFoods") or []
    disliked = profile.get("dislikedFoods") or []

    return f"""사용자: {profile.get('age')}세 {profile.get('gender')}, {profile.get('height')}cm/{profile.get('weight')}kg
목표: {goal.get('goalType')}, {meal_count}끼/일
알레르기: {allergies}
선호: {liked}

JSON (7일, dayOffset 0~6):
{{
  "weekMeals": [
    {{"dayOffset": 0, "meals": [{{"mealTime": "BREAKFAST", "foodName": "메뉴명", "servingSize": "1인분"}}]}}
  ]
}}
"""


def get_generate_month_prompt(profile: Dict[str, Any], goal: Dict[str, Any]) -> str:
    meal_count = goal.get("mealCount") or 3
    allergies = profile.get("allergies") or []
    liked = profile.get("likedFoods") or []
    disliked = profile.get("dislikedFoods") or []

    return f"""사용자: {profile.get('age')}세 {profile.get('gender')}, {profile.get('height')}cm/{profile.get('weight')}kg
목표: {goal.get('goalType')}, {meal_count}끼/일
알레르기: {allergies}
선호: {liked}

JSON (30일, dayOffset 0~29):
{{
  "monthMeals": [
    {{"dayOffset": 0, "meals": [{{"mealTime": "BREAKFAST", "foodName": "메뉴명", "servingSize": "1인분"}}]}}
  ]
}}
"""


def get_generate_days_prompt(profile: Dict[str, Any], goal: Dict[str, Any], days: int) -> str:
    meal_count = goal.get("mealCount") or 3
    allergies = profile.get("allergies") or []
    liked = profile.get("likedFoods") or []
    disliked = profile.get("dislikedFoods") or []
    total_days = max(1, int(days or 1))

    return f"""사용자: {profile.get('age')}세 {profile.get('gender')}, {profile.get('height')}cm/{profile.get('weight')}kg
목표: {goal.get('goalType')}, {meal_count}끼/일
알레르기: {allergies}
선호: {liked}

JSON ({total_days}일, dayOffset 0~{total_days - 1}):
{{
  "daysMeals": [
    {{"dayOffset": 0, "meals": [{{"mealTime": "BREAKFAST", "foodName": "메뉴명", "servingSize": "1인분"}}]}}
  ]
}}
"""


