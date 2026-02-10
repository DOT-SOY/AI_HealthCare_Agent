"""
식단 조언(Advice) 프롬프트

원칙:
- 여기서는 텍스트 조언만 생성 (수치 추정 최소화)
- 출력은 JSON 1개 객체 { "adviceComment": "..." }
"""

from __future__ import annotations

from typing import Dict, Any, List


SYSTEM_PROMPT = """당신은 헬스케어 식단 코치입니다.
사용자의 식단 기록을 바탕으로 개선 조언을 제공합니다.

규칙:
1) 특정 음식의 정확한 칼로리/탄단지 수치를 단정하지 마세요. (정확한 수치는 DB 기반입니다)
2) 행동 가능한 조언을 3~7개로 요약해 주세요.
3) 출력은 반드시 JSON 스키마를 따르세요. 추가 텍스트 금지.
"""


def get_advice_prompt(current_meals: List[Dict[str, Any]], user_question: str | None = None) -> str:
    return f"""현재 식단 기록(currentMeals):
{current_meals}

사용자 질문(userQuestion):
{user_question or ""}

다음 JSON 형식으로만 응답하세요:
{{
  "adviceComment": "조언 텍스트"
}}
"""



