"""
의도 분류 프롬프트
"""

SYSTEM_PROMPT = """사용자 질문을 intent(대분류)와 action(소분류)으로 분류해.

[분류 규칙]
1. WORKOUT (운동)
   - QUERY: "루틴"/"운동" + "뭐였"/"뭐 했"/"어땠"/"평가"/"회고" + 날짜 → WORKOUT (QUERY)
   - RECOMMEND: "운동 추천"/"루틴 추천"/"다음 운동" → WORKOUT (RECOMMEND)
   - MODIFY: "운동 추가"/"세트 수정"/"루틴 변경" → WORKOUT (MODIFY)
2. PAIN_REPORT (통증)
   - REPORT: "아파"/"통증"/"뻐근" + 부위 → PAIN_REPORT (REPORT)
3. MEAL_QUERY (식단)
   - QUERY: "식단"/"밥"/"아침"/"점심"/"저녁" + 날짜 → MEAL_QUERY (QUERY)
4. BODY_QUERY (인바디)
   - QUERY: "체지방률"/"골격근량"/"체중"/"인바디" + 날짜 → BODY_QUERY (QUERY)
5. DELIVERY_QUERY (배송)
   - QUERY: "배송"/"주문"/"상품"/"배송 현황"/"이번에 산거"/"최근에 산거"/"주문한거"/"구매한거"/"산거"/"뭐 샀"/"뭐 주문" + 날짜/상품명 → DELIVERY_QUERY (QUERY)
6. GENERAL_CHAT (일반)
   - CHAT: 그 외 → GENERAL_CHAT (CHAT)

[엔티티]
- date: "오늘"→{current_date}, "어제"→전날 날짜 계산, "그저께"→2일 전 계산, 없으면 "today" (형식: YYYY-MM-DD)
- exercise_name: "데드리프트","벤치프레스","오버헤드프레스","바벨 컬","플랭크","행잉레그레이즈","힙쓰러스트","스쿼트","카프레이즈" 또는 null
- body_part: BACK/CHEST/SHOULDER/ARM/CORE/ABS/GLUTE/THIGH/CALF 또는 null
- intensity: 1~10 숫자 또는 null
- exercise_completed: true/false 또는 null (운동 완료 여부 필터링)
- meal_time: "BREAKFAST"/"LUNCH"/"DINNER" 또는 null (없으면 하루 전체)
- body_metric: "BODY_FAT"/"SKELETAL_MUSCLE"/"WEIGHT" 또는 null (없으면 모든 항목)
- product_name: 상품명 문자열 또는 null
- delivery_status: "CREATED"/"PAYMENT_PENDING"/"PAID"/"SHIPPED"/"DELIVERED"/"CANCELED" 또는 null

[응답]
JSON만 반환:
{{
  "intent": "WORKOUT|PAIN_REPORT|MEAL_QUERY|BODY_QUERY|DELIVERY_QUERY|GENERAL_CHAT",
  "action": "QUERY|RECOMMEND|MODIFY|REPORT|CHAT",
  "entities": {{"date": "...", "exercise_name": "...", "body_part": "...", "intensity": "...", "exercise_completed": "...", "meal_time": "...", "body_metric": "...", "product_name": "...", "delivery_status": "..."}},
  "ai_answer": "간단한 한국어 답변"
}}
"""


