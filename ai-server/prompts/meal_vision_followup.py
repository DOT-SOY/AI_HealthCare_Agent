"""
이미지(음식) 분석 후 후속 사용자 발화 의도 판별 프롬프트

목표:
- 사용자의 자연어(어떤 표현이든)를 보고
  - ADD(추가)
  - REPLACE(기존 끼니 대체/변경)
  - CANCEL(취소)
  - ASK(정보 부족 -> 되물음)
  를 판별한다.

주의:
- 이 단계에서는 "행동"만 결정한다. (DB 수정은 백엔드/프론트가 수행)
"""

from __future__ import annotations

from typing import Any, Dict


SYSTEM_PROMPT = """당신은 헬스케어 앱의 식단 비서입니다.
사용자는 음식 사진을 올렸고, 시스템은 이미지에서 음식명/영양정보를 이미 추출했습니다.
이제 사용자의 후속 발화(자연어)를 보고, 어떤 행동을 해야 하는지 JSON으로만 결정하세요.

[가능한 operation]
- ADD: 오늘 식단에 '추가 섭취'로 기록
- REPLACE: 오늘 식단의 특정 끼니(아침/점심/저녁)를 이 음식으로 '대체/변경'하여 기록
- CANCEL: 반영하지 않음
- ASK: 사용자의 의도가 불명확해서 되물어야 함

[mealTime]
사용자가 끼니를 명시하면 반드시 지정하세요:
- BREAKFAST / LUNCH / DINNER
사용자가 끼니를 명시하지 않았으면 null로 두세요. (시스템이 시간대 기반으로 추정할 수 있음)

[출력 규칙]
- JSON만 출력. 추가 텍스트 금지.
- 아래 스키마를 정확히 따르세요.

{
  "operation": "ADD|REPLACE|CANCEL|ASK",
  "mealTime": "BREAKFAST|LUNCH|DINNER|null",
  "assistantReply": "사용자에게 보여줄 한국어 한두 문장"
}

[판단 가이드]
- 사용자가 '추가/기록/먹었어/넣어줘/반영해' 등 -> ADD
- 사용자가 '바꿔/대체/변경/이걸로 할래/이걸로 해줘' 등 -> REPLACE
- 사용자가 '아니/취소/그만/안할래' 등 -> CANCEL
- 의도나 끼니가 애매하면 -> ASK (예: '그래', '응', '해줘'만 있는 경우)
"""


def get_followup_prompt(analyzed_food: Dict[str, Any], user_text: str) -> str:
    food_name = (analyzed_food or {}).get("foodName") or "알 수 없음"
    cal = (analyzed_food or {}).get("calories")
    carbs = (analyzed_food or {}).get("carbs")
    protein = (analyzed_food or {}).get("protein")
    fat = (analyzed_food or {}).get("fat")

    return f"""[이미지 분석 결과]
- foodName: {food_name}
- calories: {cal}
- carbs: {carbs}
- protein: {protein}
- fat: {fat}

[사용자 발화]
{user_text}
"""



