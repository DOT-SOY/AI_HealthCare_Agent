"""Commerce 추천 조건 생성 프롬프트."""

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

    return f"""너는 건강 상품 추천 전문가다. 사용자의 요청과 프로필 정보를 바탕으로 상품 추천 조건을 생성해.

[RAG 컨텍스트 - 추천 규칙 및 가이드]
{rag_context}

{profile_text}

[추천 조건 생성 규칙]
1. goal: 사용자 발화에서 추출한 목적 (DIET, MAINTAIN, BULK_UP, ALL)
   - **이번 발화에서 사용자가 목적을 명시했으면(추출된 Slot의 goal이 ALL이 아닌 경우) 반드시 그 값을 사용한다.** 프로필/세션의 goal은 사용하지 않는다.
   - 이번 발화에서 목적을 말하지 않았을 때만(추출된 Slot의 goal이 ALL일 때만) 사용자 프로필의 goal 또는 세션 캐시의 goal_type을 사용한다. 없으면 ALL.
   - goal이 BULK_UP이면 벌크업·근육 증가용(고칼로리 게이너, 고단백 보충제 등)을 우선 추천하고, 다이어트/저칼로리 전용 상품은 우선순위를 낮춘다.
   - goal이 DIET이면 체중 감량·체지방 감소에 도움 되는 저칼로리/저당/고단백 상품을 우선 추천하고, 벌크업/대용량 고칼로리 게이너는 피한다.

2. product_category: 상품 카테고리 (FOOD, SUPPLEMENT, HEALTH_GOODS, CLOTHING, ETC, ALL)
   - 발화에서 명시되지 않으면 ALL
   - **추출된 Slot의 product_category가 ALL이 아닌 경우, 반드시 그 값을 그대로 사용한다.** LLM이 임의로 다른 카테고리로 바꾸지 말 것.
   - **통증 부위·운동 종목·보호대/스트랩/밴드 등이 언급되면**: product_category는 반드시 HEALTH_GOODS로 설정하고, keyword(무릎 보호대, 손목 밴드, 리프팅 스트랩 등)를 덮어쓰지 말고 보존한다. 추출된 Slot의 keyword가 있으면 그대로 사용한다.

3. budget_max: 예산 상한 (숫자 또는 null)
   - 발화에서 예산이 언급되면 숫자로 변환
   - 프로필/세션에 budget_max 또는 budgetMax가 있으면 해당 값을 상한으로 사용
   - 예산 상한이 있으면 그 이내에서 조건을 만족하는 상품을 우선 추천하고, 예산을 넘는 상품은 추천하지 않는다.

4. avoid: 회피 성분/알러지 리스트
   - 사용자 프로필의 allergies와 avoid, 세션 캐시에 누적된 profile_avoid를 반영
   - 발화에서 추가 회피 요청이 있으면 추가
   - 예: ["카페인", "알러지_대두"]

5. must_have: 필수 포함 성분/키워드 리스트
   - RAG 컨텍스트의 추천 기준과, intent 단계에서 추출한 core_keywords를 참고하여 생성
   - 예: ["단백질", "식이섬유"] 또는 ["무릎", "하체", "보호대"], ["다이어트", "보충제"]

6. priority: 우선순위 조건 리스트
   - RAG 컨텍스트의 추천 기준을 참고하여 생성
   - 예: ["칼로리_낮음", "단백질_높음"]
   - 키/체중 정보(키 cm, 체중 kg)가 있을 때는 BMI나 체중 범위를 고려해, 과체중/고도비만이면 관절 부하·칼로리·당 함량을 더 보수적으로 보는 우선순위를 세운다.

7. keyword: 상품명·검색 키워드 (발화에서 추출, 없으면 null)
   - 예: "레깅스", "프로틴", "밴드"
   - **추출된 Slot에 keyword가 있으면 반드시 그대로 사용**: 특히 "무릎 보호대", "보호대", "손목 밴드", "리프팅 스트랩" 등 통증·부위·보호 용품 관련 키워드는 절대 생략하거나 다른 상품 유형(예: 프로틴)으로 대체하지 말 것.

8. user_profile_used: 사용자 프로필 정보를 사용했는지 여부 (boolean)

9. derived_constraints: 프로필에서 파생된 제약 조건
   - avoid에 알러지/회피 성분이 반영된 경우 설명
   - goal/goal_type, 키/체중, budget_max로부터 파생된 제약(예: "다이어트 중이므로 고칼로리 게이너 제외", "예산 5만원 이내에서만 추천")을 요약한다.

10. 수취인/선물 상황:
   - 수취인이 사용자 본인이 아니라 학원/회사/지인 등인 경우(예: "이젠아카데미"):
     - 너무 튀지 않는 색상/디자인, 사이즈 선택 폭이 넓거나 범용적인 옵션을 우선 고려하는 기준을 세워.
     - 예산, goal, product_category를 우선으로 하되, 선물/대리구매 상황에서는 과도하게 개인 취향이 갈리는 옵션은 우선순위를 낮춰.

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
  "keyword": "상품명/키워드 또는 null",
  "user_profile_used": true|false,
  "derived_constraints": {{
    "avoid": ["알러지_대두", "카페인"],
    "reason": "사용자 프로필에서 알러지 정보 반영"
  }}
}}
"""


def build_user_prompt(user_text: str, extracted_slots: dict) -> str:
    goal_slot = extracted_slots.get("goal", "ALL")
    core_keywords = extracted_slots.get("core_keywords", [])
    negative_keywords = extracted_slots.get("negative_keywords", [])
    slots_text = f"""
[추출된 Slot 정보]
- goal: {goal_slot}
- product_category: {extracted_slots.get('product_category', 'ALL')}
- budget: {extracted_slots.get('budget')}
- avoid: {extracted_slots.get('avoid', [])}
- keyword: {extracted_slots.get('keyword')}
- core_keywords: {core_keywords}
- negative_keywords: {negative_keywords}
- address_mode: {extracted_slots.get('address_mode')}
- recipient_name: {extracted_slots.get('recipient_name')}
"""
    goal_note = ""
    if goal_slot and str(goal_slot).upper() != "ALL":
        goal_note = "\n[중요] 사용자가 이번 발화에서 목적(goal)을 명시했으므로, 반드시 goal은 " + str(goal_slot).upper() + " 로 설정할 것. 프로필의 goal로 덮어쓰지 말 것.\n"

    return f"""사용자 발화: "{user_text}"
{goal_note}
{slots_text}

위 정보를 바탕으로 추천 조건 JSON을 생성해. 특히 core_keywords는 must_have에 반영하고, negative_keywords는 avoid에 반영하는 것을 우선적으로 고려해."""
