"""
Commerce 상품 추천 조건 생성 프롬프트
"""

def build_system_prompt(rag_context: str, user_profile: dict) -> str:
    """
    시스템 프롬프트 생성
    
    Args:
        rag_context: RAG 검색 결과 컨텍스트
        user_profile: 사용자 프로필 정보
    
    Returns:
        시스템 프롬프트 문자열
    """
    profile_text = ""
    if user_profile:
        profile_parts = []
        if user_profile.get("heightCm"):
            profile_parts.append(f"키: {user_profile['heightCm']}cm")
        if user_profile.get("weightKg"):
            profile_parts.append(f"체중: {user_profile['weightKg']}kg")
        if user_profile.get("bodyFatPercent"):
            profile_parts.append(f"체지방률: {user_profile['bodyFatPercent']}%")
        if user_profile.get("bodyWaterPercent"):
            profile_parts.append(f"체수분: {user_profile['bodyWaterPercent']}%")
        if user_profile.get("goal"):
            profile_parts.append(f"운동 목적: {user_profile['goal']}")
        if user_profile.get("allergies"):
            profile_parts.append(f"알러지: {', '.join(user_profile['allergies'])}")
        if user_profile.get("avoid"):
            profile_parts.append(f"회피 성분: {', '.join(user_profile['avoid'])}")
        
        if profile_parts:
            profile_text = f"\n[사용자 프로필]\n" + "\n".join(profile_parts)
    
    return f"""너는 건강 상품 추천 전문가다. 사용자의 요청과 프로필 정보를 바탕으로 상품 추천 조건을 생성해.

[RAG 컨텍스트 - 추천 규칙 및 가이드]
{rag_context}

{profile_text}

[추천 조건 생성 규칙]
1. goal: 사용자 발화에서 추출한 목적 (DIET, MAINTAIN, BULK_UP, ALL)
   - 사용자 프로필의 goal이 있으면 우선 사용
   - 없으면 발화에서 추출

2. product_category: 상품 카테고리 (FOOD, SUPPLEMENT, HEALTH_GOODS, CLOTHING, ETC, ALL)
   - 발화에서 명시되지 않으면 ALL

3. budget_max: 예산 상한 (숫자 또는 null)
   - 발화에서 예산이 언급되면 숫자로 변환
   - 없으면 null

4. avoid: 회피 성분/알러지 리스트
   - 사용자 프로필의 allergies와 avoid를 반영
   - 발화에서 추가 회피 요청이 있으면 추가
   - 예: ["카페인", "알러지_대두"]

5. must_have: 필수 포함 성분 리스트
   - RAG 컨텍스트의 추천 기준을 참고하여 생성
   - 예: ["단백질", "식이섬유"]

6. priority: 우선순위 조건 리스트
   - RAG 컨텍스트의 추천 기준을 참고하여 생성
   - 예: ["칼로리_낮음", "단백질_높음"]

7. user_profile_used: 사용자 프로필 정보를 사용했는지 여부 (boolean)

8. derived_constraints: 프로필에서 파생된 제약 조건
   - avoid에 알러지/회피 성분이 반영된 경우 설명

[주의사항]
- 의료적 효능을 단정하지 말 것 (예: "치료한다", "완치한다" 금지)
- 모든 추천은 "도움이 될 수 있습니다" 수준으로 표현
- 개인차가 있을 수 있음을 고지
- 상품명, 가격, 재고 정보는 언급하지 말 것 (조건만 생성)

[응답 형식]
반드시 JSON만 반환 (자연어 금지):
{{
  "goal": "DIET|MAINTAIN|BULK_UP|ALL",
  "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
  "budget_max": 숫자 또는 null,
  "avoid": ["키워드1", "키워드2", ...],
  "must_have": ["키워드1", "키워드2", ...],
  "priority": ["조건1", "조건2", ...],
  "user_profile_used": true|false,
  "derived_constraints": {{
    "avoid": ["알러지_대두", "카페인"],
    "reason": "사용자 프로필에서 알러지 정보 반영"
  }}
}}
"""


def build_user_prompt(user_text: str, extracted_slots: dict) -> str:
    """
    사용자 프롬프트 생성
    
    Args:
        user_text: 사용자 발화
        extracted_slots: 추출된 slot 정보
    
    Returns:
        사용자 프롬프트 문자열
    """
    slots_text = f"""
[추출된 Slot 정보]
- goal: {extracted_slots.get('goal', 'ALL')}
- product_category: {extracted_slots.get('product_category', 'ALL')}
- budget: {extracted_slots.get('budget')}
- avoid: {extracted_slots.get('avoid', [])}
"""
    
    return f"""사용자 발화: "{user_text}"

{slots_text}

위 정보를 바탕으로 추천 조건 JSON을 생성해."""

