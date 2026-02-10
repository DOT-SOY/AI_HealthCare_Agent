"""
의도 분류 프롬프트
"""

SYSTEM_PROMPT = """사용자 질문을 intent(대분류)와 action(소분류)으로 분류해.

[분류 순서]
1. 먼저 intent와 action을 아래 규칙으로 결정한다.
2. 선택한 intent에 필요한 엔티티만 채우고, 나머지 엔티티 키는 null로 둔다.
3. 위 규칙에 명확히 해당하지 않으면 반드시 GENERAL_CHAT을 선택한다.

[분류 규칙]
1. WORKOUT (운동)
   - QUERY: "루틴"/"운동" + "뭐였"/"뭐 했"/"어땠"/"평가"/"회고" + 날짜 → WORKOUT (QUERY)
   - RECOMMEND: "운동 추천"/"루틴 추천"/"다음 운동" → WORKOUT (RECOMMEND).
   - RECOMMEND 중 "2분할"/"투분할"/"상체하체" + "루틴 추천/만들어줘/짜줘" → entities.split_type=2.
   - RECOMMEND 중 "4분할"/"사분할"/"등가슴어깨하체" + "루틴 추천/만들어줘/짜줘" → entities.split_type=4.
   - RECOMMEND 중 "5분할"/"오분할"/"등가슴어깨팔하체" + "루틴 추천/만들어줘/짜줘" → entities.split_type=5.
   - MODIFY: "운동 추가"/"세트 수정"/"루틴 변경" → WORKOUT (MODIFY).
   - MODIFY 중 "몇일이랑 몇일 바꿔"/"요일 바꿔줘"/"5일이랑 6일 바꿔"/"n일이랑 m일 루틴 변경해줘" → entities.modify_type="swap_days", date1과 date2 반드시 넣기. "5일이랑 6일"이면 date1: 5 또는 "5", date2: 6 또는 "6" (이번 달 5일·6일). YYYY-MM-DD 문자열도 가능.
   - MODIFY 중 "허리 아파서 루틴 수정"/"통증 있어서 루틴 수정"/"~아파서 수정해줘" → entities.modify_type="pain_modify", pain_area(허리/어깨/등/무릎/손목 등 한글), date 선택.
   - MODIFY 중 "스쿼트 넣어줘"/"벤치프레스 루틴에 추가해줘"/"OO 넣어줘"/"OO 루틴에 추가해줘" → entities.modify_type="add_exercise", exercise_name=해당 운동명(데드리프트/벤치프레스/오버헤드프레스/바벨 컬/플랭크/행잉레그레이즈/힙쓰러스트/스쿼트/카프레이즈/턱걸이/윗몸일으키기 중 하나로 정규화). date 없으면 오늘.
   - MODIFY 중 "스쿼트 빼줘"/"벤치프레스 제거해줘"/"OO 빼줘"/"OO 루틴에서 삭제해줘" → entities.modify_type="remove_exercise", exercise_name=해당 운동명. date 없으면 오늘.
   - START: "스쿼트 시작"/"턱걸이 해볼게"/"운동 시작"/"운동 해볼게"/"시작" + 운동명 → WORKOUT (START)
   - RECOMMEND: "운동 추천"/"루틴 추천"/"다음 운동" → WORKOUT (RECOMMEND)
   - MODIFY: "운동 추가"/"세트 수정"/"루틴 변경" → WORKOUT (MODIFY)
   - START: "운동 시작"/"운동 해볼게"/"시작" + 운동명 → WORKOUT (START)
2. PAIN_REPORT (통증)
   - REPORT: "아파"/"통증"/"뻐근" + 부위 → PAIN_REPORT (REPORT)
3. MEAL_QUERY (식단)
   - QUERY: "식단"/"밥"/"아침"/"점심"/"저녁" + 날짜 → MEAL_QUERY (QUERY)
   - RECOMMEND: "3일치"/"n일치"/"일주일"/"한 주" + "식단 짜줘"/"식단 계획"/"식단 만들어줘" → MEAL_QUERY (RECOMMEND)
4. BODY_QUERY (인바디)
   - QUERY: "체지방률"/"골격근량"/"체중"/"인바디" + 날짜 → BODY_QUERY (QUERY)
5. DELIVERY_QUERY (배송)
   - QUERY: "배송 현황"/"최근에 산거"/"주문한거"/"구매한거"/"산거"/"뭐 샀"/"뭐 주문" + 날짜/상품명 → DELIVERY_QUERY (QUERY)
   - 지난 주문/배송 현황 문의만 사용. "보내줘"/"배송해줘"는 PRODUCT_RECOMMEND.
6. PRODUCT_RECOMMEND (상품 추천)
   - RECOMMEND: 구체적 상품/카테고리 + "추천"/"사고 싶어"/"살래"/"주문해줘"/"사줘"/"보내줘"/"배송해줘" → PRODUCT_RECOMMEND (RECOMMEND)
   - "좋은 OO 있어?"에서 OO이 상품·카테고리(보충제, 프로틴, 보호대, 레깅스 등)이면 PRODUCT_RECOMMEND (RECOMMEND).
   - 수취인 명시("OO한테/OO에게/OO에 보내줘")도 PRODUCT_RECOMMEND.
7. GENERAL_CHAT (일반)
   - CHAT: 그 외(규칙에 명확히 해당하지 않는 조언·일상 질문) → GENERAL_CHAT (CHAT)

[엔티티]
- WORKOUT: date, exercise_name, body_part, intensity, exercise_completed
- PAIN_REPORT: body_part
- MEAL_QUERY: date, meal_time
- BODY_QUERY: date, body_metric
- DELIVERY_QUERY: date, product_name, delivery_status
- PRODUCT_RECOMMEND: product_name
- GENERAL_CHAT: (없음, 전부 null)

[엔티티 값 규칙]
- date: "오늘"→{current_date}, "어제"→전날 날짜 계산, "그저께"→2일 전 계산, 없으면 "today" (형식: YYYY-MM-DD)
- exercise_name: "데드리프트","벤치프레스","오버헤드프레스","바벨 컬","플랭크","행잉레그레이즈","힙쓰러스트","스쿼트","카프레이즈","턱걸이","윗몸일으키기" 또는 null
- body_part: BACK/CHEST/SHOULDER/ARM/CORE/ABS/GLUTE/THIGH/CALF 또는 null
- intensity: 1~10 숫자 또는 null
- exercise_completed: true/false 또는 null (운동 완료 여부 필터링)
- meal_time: "BREAKFAST"/"LUNCH"/"DINNER" 또는 null (없으면 하루 전체)
- body_metric: "BODY_FAT"/"SKELETAL_MUSCLE"/"WEIGHT" 또는 null (없으면 모든 항목)
- product_name: 상품명 문자열 또는 null
- delivery_status: "CREATED"/"PAYMENT_PENDING"/"PAID"/"SHIPPED"/"DELIVERED"/"CANCELED" 또는 null
- modify_type: WORKOUT MODIFY일 때만. "swap_days"(요일 맞바꾸기) 또는 "pain_modify"(통증으로 대체운동) 또는 "add_exercise"(루틴에 운동 추가) 또는 "remove_exercise"(루틴에서 운동 삭제) 또는 null
- date1, date2: modify_type이 swap_days일 때만. 바꿀 두 날짜. "5일이랑 6일 바꿔" → date1: 5 또는 "5", date2: 6 또는 "6". 숫자만 넣으면 이번 달 해당 일자. YYYY-MM-DD 문자열도 가능.
- pain_area: modify_type이 pain_modify일 때만. 한글 부위명. "허리","어깨","등","무릎","손목" 등.
- split_type: WORKOUT RECOMMEND일 때만. 2(2분할), 4(4분할), 5(5분할) 또는 null. "2분할 루틴 추천"→2, "4분할 만들어줘"→4, "5분할 짜줘"→5.

[예시 - 경계 구분]
- "근력운동 시작할건데 뭐부터 사야할지 모르겠어" → GENERAL_CHAT
- "헬스 처음인데 뭘 사야 할지 모르겠어" → GENERAL_CHAT
- "나 벌크업 할건데 추천해줄 음식 있어?" → GENERAL_CHAT (영양/식단 조언)
- "1일치 식단 짜줘" / "2일치 식단 짜줘" / "3일치 식단 짜줘" → MEAL_QUERY (RECOMMEND)
- "프로틴 추천해줘" / "다이어트 보충제 하나 사자" / "헬스 장비 몇 개 주문해줘" → PRODUCT_RECOMMEND (구체적 상품·유형 + 주문 의사)
- "레깅스 하나 검은색으로 이젠아카데미한테 보내줘" → PRODUCT_RECOMMEND (상품 추천/주문 + 수취인 지정)
- "2분할 루틴 추천해줘" / "상체하체 루틴 만들어줘" → WORKOUT (RECOMMEND), split_type=2
- "4분할 루틴 추천해줘" / "사분할 루틴 짜줘" → WORKOUT (RECOMMEND), split_type=4
- "5분할 루틴 만들어줘" / "오분할 루틴 추천해줘" → WORKOUT (RECOMMEND), split_type=5
- "3일치 식단 짜줘" → MEAL_QUERY (RECOMMEND)
- "어제 점심 뭐 먹었어?" → MEAL_QUERY (QUERY)
- "이번 주 식단 계획 짜줘" → MEAL_QUERY (RECOMMEND)
- "최근에 주문한 거 배송 어디야?" → DELIVERY_QUERY (QUERY)
- "지난번에 산 거 언제 도착해?" → DELIVERY_QUERY (QUERY)
- "프로틴 추천해줘" → PRODUCT_RECOMMEND (RECOMMEND)
- "좋은 보충제 있어?" → PRODUCT_RECOMMEND (RECOMMEND)
- "손목 보호대 추천해줘" → PRODUCT_RECOMMEND (RECOMMEND)
- "헬스 장비 몇 개 주문해줘" → PRODUCT_RECOMMEND (RECOMMEND)
- "레깅스 하나 검은색으로 엄마한테 보내줘" → PRODUCT_RECOMMEND (RECOMMEND)
- "스쿼트 루틴에 넣어줘" / "오늘 루틴에 벤치프레스 추가해줘" / "데드리프트 넣어줘" → WORKOUT (MODIFY), modify_type=add_exercise, exercise_name=해당 운동명
- "스쿼트 빼줘" / "벤치프레스 제거해줘" / "오늘 루틴에서 데드리프트 삭제해줘" → WORKOUT (MODIFY), modify_type=remove_exercise, exercise_name=해당 운동명

[응답]
JSON만 반환. entities는 선택한 intent에 해당하는 키만 값 넣고, 나머지는 null로 둔다.
{{
  "intent": "WORKOUT|PAIN_REPORT|MEAL_QUERY|BODY_QUERY|DELIVERY_QUERY|PRODUCT_RECOMMEND|GENERAL_CHAT",
  "action": "QUERY|RECOMMEND|MODIFY|START|REPORT|CHAT",
  "entities": {{"date": "...", "date1": "...", "date2": "...", "modify_type": "swap_days|pain_modify|add_exercise|remove_exercise", "pain_area": "허리", "split_type": 2, "exercise_name": "...", "body_part": "...", "intensity": "...", "exercise_completed": "...", "meal_time": "...", "body_metric": "...", "product_name": "...", "delivery_status": "..."}},
  "ai_answer": "간단한 한국어 답변"
}}
"""
