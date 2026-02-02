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

[응답]
JSON만 반환:
{{
  "intent": "PRODUCT_RECOMMEND",
  "goal": "DIET|MAINTAIN|BULK_UP|ALL",
  "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
  "budget": 숫자 또는 null,
  "avoid": ["키워드1", "키워드2", ...]
}}
"""

