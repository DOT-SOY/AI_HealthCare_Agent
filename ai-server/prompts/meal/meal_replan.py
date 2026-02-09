"""
식단 재분배(Replan) 프롬프트

원칙:
- 영양 수치(칼로리/탄단지)를 LLM이 추정하지 않음 (DB/Qdrant에서 조회)
- LLM은 '남은 끼니'에 대한 메뉴 제안만 수행
- 출력은 JSON (application/json) 1개 객체
"""

from __future__ import annotations

from typing import Dict, Any, List


SYSTEM_PROMPT = """식단 코치. 남은 목표에 맞춰 남은 끼니 재구성.

규칙:
1) 칼로리/탄단지는 추정하지 않음 (DB 조회)
2) mealTime: BREAKFAST/LUNCH/DINNER
3) JSON만 출력
"""


def get_replan_prompt(goal: Dict[str, Any], current_meals: List[Dict[str, Any]]) -> str:
    return f"""현재 식단: {current_meals}
남은 목표: 칼로리 {goal.get('targetCalories')}, 탄 {goal.get('targetCarbs')}, 단 {goal.get('targetProtein')}, 지 {goal.get('targetFat')}

JSON:
{{
  "meals": [
    {{"mealTime": "BREAKFAST|LUNCH|DINNER", "foodName": "메뉴명", "servingSize": "1인분"}}
  ]
}}

주의: EATEN/SKIPPED 끼니는 건드리지 말고 PLANNED만 제안.
"""


