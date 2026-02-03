"""
Commerce 의도 분류 및 Slot 추출 프롬프트
"""

SYSTEM_PROMPT = """사용자의 상품 추천 요청에서 intent와 slot을 추출해.

[Intent 분류]
- PRODUCT_RECOMMEND: 상품 추천 요청 ("추천해줘", "추천", "어떤게 좋아", "뭐 살까", "구매하고 싶어" 등)

[Slot 추출]
1. goal: 운동 목적
   - "다이어트", "체중 감량", "살 빼기" → DIET
   - "유지", "현재 유지" → MAINTAIN
   - "벌크업", "근육 증가", "증량" → BULK_UP
   - 없으면 ALL

2. product_category: 상품 카테고리
   - "음식", "식품" → FOOD
   - "보충제", "영양제", "프로틴", "비타민" → SUPPLEMENT
   - "헬스용품", "운동용품", "기구" → HEALTH_GOODS
   - "의류", "운동복" → CLOTHING
   - 없으면 ALL

3. budget: 예산 (숫자만 추출, 단위는 무시)
   - "5만원", "50000원", "5만" → 50000
   - 없으면 null

4. avoid: 회피 성분/알러지 (간단한 키워드만)
   - "카페인", "커피" → ["카페인"]
   - "대두", "콩" → ["알러지_대두"]
   - "유제품", "우유" → ["알러지_유제품"]
   - 없으면 []

5. keyword: 상품명·검색 키워드 (예: 레깅스, 프로틴, 밴드). 없으면 null

6. variant_option: 색상·사이즈 등 옵션 (예: 검은색, 빨간색, L, 95). 없으면 null

7. address_mode: 배송지 모드
   - "기본배송지", "원래 주소", "기존 배송지" → "DEFAULT"
   - "다른 주소", "새 주소", "회사로", "친구 집으로" 등 기존과 다른 곳을 명시 → "NEW"
   - 명확하지 않으면 null

8. pending_action: 이번 발화로 사용자가 바로 요청한 다음 액션
   - "결제할게", "바로 결제", "지금 결제해줘" 등 → "PAYMENT"
   - "보내줘", "보내 주세요", "배송해줘", "주문해줘"처럼 바로 보내 달라는 표현도 → "PAYMENT"
   - 단순 추천 요청(예: "추천해줘", "뭐 살까?")은 null

9. recipient_name: 수취인/배송지 이름
   - "OOO한테", "OOO에게", "OOO에 보내줘" → "OOO"
   - 예: "레깅스 하나 검은색으로 이젠아카데미한테 보내줘" → "이젠아카데미"
   - 없으면 null

10. needs_personalization: 이번 추천이 사용자 본인의 몸 상태/목표/프로필을 활용한 개인화가 필요한지 여부
    - "나한테 필요한", "내가 먹을", "나 요즘 벌크업해야 하는데", "나 보충제 사야 해"처럼 1인칭 + 운동/보충제/건강 관련 표현 → true
    - 선물/대리구매 중심 표현(예: "이젠아카데미한테 뭐 사주고 싶어", "친구 선물") → false
    - 명확하지 않을 때는 false

[응답]
JSON만 반환:
{{
  "intent": "PRODUCT_RECOMMEND",
  "goal": "DIET|MAINTAIN|BULK_UP|ALL",
  "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
  "budget": 숫자 또는 null,
  "avoid": ["키워드1", "키워드2", ...],
  "keyword": "상품명 또는 null",
  "variant_option": "색상/사이즈 또는 null",
  "address_mode": "DEFAULT|NEW|null",
  "pending_action": "PAYMENT|null",
  "recipient_name": "이름 또는 null",
  "needs_personalization": true 또는 false
}}
"""
