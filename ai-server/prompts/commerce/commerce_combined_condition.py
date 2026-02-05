"""Commerce 슬롯+추천 조건 통합 프롬프트."""

def build_system_prompt(rag_context: str, user_profile: dict) -> str:
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
        if user_profile.get("budgetMax"):
            profile_parts.append(f"예산 상한: {user_profile['budgetMax']}원 이내")
        if user_profile.get("allergies"):
            profile_parts.append(f"알러지: {', '.join(user_profile['allergies'])}")
        if user_profile.get("avoid"):
            profile_parts.append(f"회피 성분: {', '.join(user_profile['avoid'])}")

        if profile_parts:
            profile_text = f"\n[사용자 프로필]\n" + "\n".join(profile_parts)

    return f"""너는 건강/운동 상품 추천 전문가다. 사용자의 요청을 읽고
1) 커머스 슬롯 정보(slots)
2) 추천 조건(condition: RecommendationCondition)
를 모두 생성해.

[RAG 컨텍스트 - 추천 규칙 및 가이드]
{rag_context}

{profile_text}

[출력 규칙]
- 반드시 JSON만 반환 (자연어 금지)
- intent는 항상 PRODUCT_RECOMMEND로 고정
- slots는 사용자의 발화에서 추출 가능한 정보를 최대한 반영
- condition은 RecommendationCondition 스키마에 맞게 작성

[JSON 스키마]
{{
  "slots": {{
    "intent": "PRODUCT_RECOMMEND",
    "goal": "DIET|MAINTAIN|BULK_UP|ALL",
    "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
    "budget": 숫자 또는 null,
    "avoid": ["키워드1", "키워드2", ...],
    "keyword": "검색 키워드 또는 null",
    "variant_option": "옵션 키워드 또는 null",
    "target_body_part": "KNEE|LOWER_BODY|WRIST|HAND|BACK|null",
    "product_usage": "PROTECTOR|EQUIPMENT|SUPPLEMENT|null",
    "experience_level": "BEGINNER|INTERMEDIATE|ADVANCED|null",
    "core_keywords": ["핵심1", "핵심2", ...],
    "negative_keywords": ["제외1", "제외2", ...],
    "address_mode": "DEFAULT|NEW|null",
    "recipient_name": "수취인 또는 null",
    "needs_personalization": true|false
  }},
  "condition": {{
    "goal": "DIET|MAINTAIN|BULK_UP|ALL",
    "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
    "budget_max": 숫자 또는 null,
    "avoid": ["키워드1", "키워드2", ...],
    "must_have": ["키워드1", "키워드2", ...],
    "priority": ["조건1", "조건2", ...],
    "keyword": "검색 키워드 또는 null",
    "search_type": "all",
    "user_profile_used": true|false,
    "derived_constraints": {{
      "avoid": ["알러지_대두", "카페인"],
      "reason": "사용자 프로필에서 알러지 정보 반영"
    }}
  }}
}}
"""


def build_user_prompt(user_text: str, extracted_slots_hint: dict) -> str:
    hint = extracted_slots_hint or {}
    core_keywords = hint.get("core_keywords", [])
    negative_keywords = hint.get("negative_keywords", [])
    slots_text = f"""
[슬롯 힌트]
- goal: {hint.get('goal', 'ALL')}
- product_category: {hint.get('product_category', 'ALL')}
- budget: {hint.get('budget')}
- avoid: {hint.get('avoid', [])}
- keyword: {hint.get('keyword')}
- variant_option: {hint.get('variant_option')}
- core_keywords: {core_keywords}
- negative_keywords: {negative_keywords}
- address_mode: {hint.get('address_mode')}
- recipient_name: {hint.get('recipient_name')}
"""
    return f"""[사용자 발화]
{user_text}

{slots_text}

위 정보를 참고해 slots와 condition을 생성해."""
