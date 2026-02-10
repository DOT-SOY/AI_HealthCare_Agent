"""
식단 목표 영양소 추론 프롬프트 (간소화 버전)

목표: 사용자 프로필과 목표를 기반으로 하루 목표 칼로리/탄수화물/단백질/지방을 추론
"""

from __future__ import annotations

from typing import Any, Dict


SYSTEM_PROMPT = """당신은 영양사입니다. 사용자의 프로필과 목표를 보고 하루 목표 영양소를 계산하세요.

[출력 형식]
JSON만 출력하세요:
{
  "targetCalories": 숫자,
  "targetCarbs": 숫자 (g),
  "targetProtein": 숫자 (g),
  "targetFat": 숫자 (g)
}

[계산 기준]
- 칼로리: BMR × 활동계수 × 목표보정 (다이어트: -15%, 벌크업: +10%, 유지: 0%)
- 단백질: 체중 × 1.6~1.8g/kg (목표에 따라 조정)
- 지방: 최소 체중 × 0.6g/kg 또는 총칼로리의 25%
- 탄수화물: 나머지 칼로리로 계산
"""


def get_target_inference_prompt(profile: Dict[str, Any], goal_type: str) -> str:
    age = profile.get("age", "알 수 없음")
    height = profile.get("height", "알 수 없음")
    weight = profile.get("weight", "알 수 없음")
    gender = profile.get("gender", "알 수 없음")
    activity = profile.get("activityLevel", "MODERATE")
    
    goal_label = {"DIET": "다이어트", "BULK_UP": "벌크업", "MAINTAIN": "유지"}.get(goal_type.upper(), "유지")
    
    return f"""사용자 프로필:
- 나이: {age}
- 키: {height} cm
- 몸무게: {weight} kg
- 성별: {gender}
- 활동수준: {activity}
- 목표: {goal_label}

위 정보를 바탕으로 하루 목표 영양소를 계산해주세요."""


