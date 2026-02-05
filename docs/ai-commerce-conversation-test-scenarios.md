## AI Commerce 핵심 대화 시나리오 테스트

이 문서는 Commerce AI 추천·구매 플로우의 핵심 UX 시나리오를 통합적으로 검증하기 위한 테스트 케이스를 정의합니다.  
테스트는 `ai-server/services/commerce_orchestration_service.handle_commerce_recommend` 엔드포인트를 기준으로 수행합니다.

---

### 1. 벌크업 보충제 개인화 시나리오

- **사전 조건**
  - Backend 프로필 API(`/api/members/me/profile`) 응답 예:
    - `goal = "BULK_UP"`, `heightCm = 175`, `weightKg = 70`, `avoid = []`
  - 프론트에서 AI 서버 호출 시 `auth_token` 정상 전달.

- **대화 흐름**
  1. 사용자: `"나 요즘 벌크업해야 하는데 보충제 사야 한다"`  
     - `extract_commerce_intent_and_slots` 결과:
       - `goal = "BULK_UP"`, `product_category = "SUPPLEMENT"`, `needs_personalization = true`
  2. `handle_recommend_state`:
     - 세션 생성 후 슬롯 병합 및 저장.
     - `needs_personalization = true` 이고 `auth_token` 존재 → `ensure_session_profile(session_id, auth_token)` 1회 호출.
     - Backend 프로필 조회 결과를 세션 캐시 필드에 채움:
       - `SessionData.goal_type = "BULK_UP"`
       - `SessionData.member_height_cm = 175.0`
       - `SessionData.member_weight_kg = 70.0`
  3. `generate_recommendation_condition`:
     - `profile_context`로 위 세션 캐시 필드를 전달.
     - LLM 프롬프트에서 BULK_UP 목표/키/몸무게를 사용해 벌크업용 보충제 위주 조건을 생성.
  4. Backend `/api/products/recommend`:
     - `goal = "BULK_UP"`, `productCategory = "SUPPLEMENT"` 조건으로 호출.

- **검증 포인트**
  - 세션당 프로필 조회가 **한 번만** 일어난다(동일 세션 내 후속 발화에서는 `ensure_session_profile`가 더 이상 Backend 호출을 하지 않음).
  - `SessionData.goal_type`이 `"BULK_UP"`으로 설정되고, 추천 조건의 `goal` 역시 `"BULK_UP"`으로 유지된다.
  - 다이어트 전용(저칼로리/감량용) 보충제보다 벌크업·근육 증가용 상품이 우선 추천되는지를 실제 추천 결과로 확인.

---

### 2. 선물/타인용 추천 시나리오

- **사전 조건**
  - 사용자 프로필은 존재하나, 이번 추천은 선물/대리구매 상황.

- **대화 흐름**
  1. 사용자: `"이젠아카데미한테 뭐 사주고 싶어"`  
     - `extract_commerce_intent_and_slots` 결과:
       - `needs_personalization = false`
       - `recipient_name = "이젠아카데미"`
  2. `handle_recommend_state`:
     - `needs_personalization = false` 이므로 `ensure_session_profile` **호출하지 않음**.
     - 세션의 `goal_type`, `member_height_cm`, `member_weight_kg`, `budget_max`는 그대로 `None` 또는 이전 값 유지.
  3. `generate_recommendation_condition`:
     - `profile_context`가 비어 있어 프로필 없이 일반적인 기준(카테고리·예산/키워드 중심)으로 조건 생성.
  4. Backend `/api/products/recommend`:
     - 선물 상황에 맞는 범용적인 상품이 우선 추천되며, 특정 체형/목표에 과도하게 특화된 상품은 상대적으로 후순위.

- **검증 포인트**
  - 해당 발화에서 프로필 조회가 발생하지 않음(Backend 프로필 API 호출 로그로 확인).
  - 추천 조건 JSON에서 프로필 기반 제약(`derived_constraints`)이 비어 있거나 최소화되어 있고, 선물/수취인 관련 규칙만 반영되는지 확인.

---

### 3. 주소/수령인 + 애매 응답 시나리오

- **사전 조건**
  - 사용자는 여러 배송지를 등록해 둔 상태이며, 그 중 하나의 수취인 이름에 `"이젠아카데미"`가 포함되어 있음.
  - 장바구니 담기까지 완료되어 `CONFIRM_ADDRESS` 상태로 진입 가능한 상황.

- **대화 흐름 (요약)**
  1. 상품/옵션 선택까지 진행 후 `CONFIRM_ADDRESS`로 전이.
  2. AI: `"배송지: {후보 주소}로 배송하시겠어요?"`
  3. 사용자: `"으"` / `"음"` / `"글쎄"` 등 애매한 응답.
  4. `handle_confirm_address_state`:
     - 긍정/부정 키워드에 모두 해당하지 않는 경우, 현재 구현에서는 다시 동일 구조의 확인 질문을 보냄.
     - 향후 UX 개선 시에는 **다른 표현의 재질문** 또는 **수취인명 추출 재시도**로 확장 가능.

- **검증 포인트**
  - 애매 응답에 대해 세션이 종료되거나 플로우가 깨지지 않고 `CONFIRM_ADDRESS` 상태가 유지되는지 확인.
  - 동일 질문 반복 대신 다른 템플릿을 사용하는 UX 개선이 필요하다면, 별도 시나리오/테스트 케이스로 추가한다.

---

### 3-1. 주소/수령인 선택 시나리오 (이젠아카데미)

- **사전 조건**
  - 저장된 배송지:
    - ① shipToName = "강민재", 주소 = "서울시 서초구 정의로 1 정도"
    - ② shipToName = "이젠아카데미", 주소 = "서초구 이젠아카데미"
  - 상품/옵션까지 선택이 완료되어 `CONFIRM_ADDRESS` 상태로 진입 가능한 상황.

- **대화 흐름**
  1. AI: `"배송지: 강민재 서울시 서초구 정의로 1 정도로 배송하시겠어요?"`  
     - `addressCandidates`에 두 주소 모두 포함되어 있음.
  2. 사용자: `"이젠아카데미"`  
     - `extract_commerce_intent_and_slots` 결과:
       - `recipient_name = "이젠아카데미"`
     - `handle_confirm_address_state`:
       - recipient_name을 이용해 shipToName이 "이젠아카데미"인 주소를 매칭.
       - 같은 `CONFIRM_ADDRESS` 상태에서 `"배송지: 이젠아카데미 서초구 이젠아카데미로 배송하시겠어요?"` 형태로 다시 물어봄.
  3. 사용자: `"응"`  
     - 긍정 응답으로 인식, 해당 주소의 id를 세션에 저장 후 `PAYMENT_READY` 상태로 전환.

- **검증 포인트**
  - `"이젠아카데미"` 한 단어만 말해도, GENERAL_CHAT/다른 도메인으로 튀지 않고 **배송지 선택용 발화**로 처리되는지 확인.
  - `handle_confirm_address_state`에서 recipient_name 매칭이 **intent 재분류(OFF_TOPIC)** 보다 먼저 시도되어 세션이 종료되지 않는지 확인.

---

### 4. 도메인 전환(쇼핑 → 다른 도메인) 시나리오

- **사전 조건**
  - Commerce 세션이 `RECOMMEND` 상태에서 정상적으로 동작 중.

- **대화 흐름**
  1. 사용자: `"프로틴 추천해줘"` → 일반적인 추천 플로우 진행.
  2. 이어서 사용자: `"그러고보니까 오늘 루틴 뭐였지?"`
  3. `handle_recommend_state` 상단:
     - `classify_intent` 호출 결과가 `PRODUCT_RECOMMEND`가 아닌 다른 인텐트(예: `ROUTINE_QUERY`)로 분류됨.
     - Commerce 세션을 `state_machine.delete_session(session_id)`로 삭제.
     - `"지금 말씀하신 내용은 상품 추천과는 다른 주제 같아요..."` 형식의 안내 메시지와 함께 `"error": "OFF_TOPIC"`을 반환.

- **검증 포인트**
  - 해당 발화에서 Commerce 세션 키가 실제로 삭제되는지(세션 저장소 조회로 확인).
  - 이후 상위 도메인(예: 운동 루틴 조회 서비스)에서 동일 텍스트를 전달했을 때, 루틴 관련 답변으로 자연스럽게 이어지는지 백엔드/프론트 레이어에서 통합 검증.

---

### 5. 벌크업 일반 질문 vs 주문 요청 시나리오

- **사전 조건**
  - 회원 프로필에 `goal = "BULK_UP"`이 설정되어 있음.

- **대화 흐름 A (일반 조언)**
  1. 사용자: `"나 벌크업 할건데 추천해줄 음식 있어?"`  
     - 상위 `/chat` 의도 분류:
       - `intent = "GENERAL_CHAT"`, `ai_answer`에 영양/식단 조언 생성.
     - Backend:
       - `GENERAL_CHAT`으로 처리, Commerce 플로우 진입하지 않음.

- **대화 흐름 B (주문 요청)**
  1. 사용자: `"나 벌크업 할건데 괜찮은 음식 주문해줘"`  
     - 상위 `/chat` 의도 분류:
       - `intent = "PRODUCT_RECOMMEND"`
     - `extract_commerce_intent_and_slots` 결과:
       - `goal = "BULK_UP"`, `product_category = "FOOD"`, `pending_action = "PAYMENT"`, `needs_personalization = true`
  2. `handle_recommend_state`:
     - 벌크업/개인화 조건으로 추천 조건 생성 후 식사/식단용 FOOD 상품 2~3개 추천.
     - `CONFIRM_PRODUCT` → `ADD_TO_CART` → `CONFIRM_ADDRESS` → `PAYMENT_READY` 순서로 이어짐.

- **검증 포인트**
  - 같은 "벌크업 + 음식" 맥락이라도, **"추천해줄 음식 있어?"**는 GENERAL_CHAT, **"음식 주문해줘"**는 PRODUCT_RECOMMEND로 분리되는지 확인.
  - 주문 요청 시 `pending_action = "PAYMENT"`가 세팅되어 결제까지 자연스럽게 이어지는지 확인.

---

## 추천 품질 검증 시나리오 (2026-02-04 추가)

로그 분석을 통해 발견된 문제점을 개선한 후, 아래 시나리오로 추천 품질을 검증합니다.

### 6. 하체 보호대 추천 시나리오

- **사용자 발화**: `"하체 운동할 건데 보호대 추천해줘"`

- **기대 조건 생성** (AI 서버)
  - `product_category = "HEALTH_GOODS"`
  - `keyword = "하체 보호대"` 또는 `"무릎 보호대"`
  - `must_have = ["보호대"]` (상품 유형만)
  - `priority = []`
  - `derived_constraints.body_parts = ["하체"]` 또는 `["하체", "무릎"]`

- **기대 추천 결과** (백엔드)
  - 1~3위에 **무릎/하체 관련 보호대** (니랩, 니슬리브 등)가 위치
  - 손목 보호대, 리프팅 스트랩은 **하위 순위**로 밀림
  - `keywordScore`에서 부위 매칭 보너스(+30) 반영 확인

- **검증 포인트**
  - 로그에서 `mustHave 분리 결과: bodyParts=[하체], typeKeywords=[보호대]` 확인
  - 로그에서 `keywordScore 우선 정렬 적용` 또는 정상적인 부위 점수 반영 확인
  - 최종 결과 1위가 하체/무릎 관련 보호대인지 확인

---

### 7. 다이어트 음식 추천 시나리오

- **사용자 발화**: `"다이어트 중인데 음식 추천해줘"`

- **기대 조건 생성** (AI 서버)
  - `goal = "DIET"`
  - `product_category = "FOOD"`
  - `keyword = "다이어트 음식"` 또는 `"다이어트 식품"`
  - `must_have = []` 또는 `["음식"]` (영양 키워드 제외)
  - `priority = ["단백질", "식이섬유", "저칼로리"]` (영양 키워드는 priority로 이동)

- **기대 추천 결과** (백엔드)
  - 다이어트 관련 식품(저칼로리 음식, 다이어트 국수 등)이 1~3위에 위치
  - `mustHave 필터 결과 0건` 경고가 발생하지 않음
  - 영양 키워드는 priority로 처리되어 점수 가중치로만 반영

- **검증 포인트**
  - 로그에서 `core_keywords 분류 완료: ... priority=[단백질, 식이섬유]` 확인
  - 로그에서 FOOD 카테고리에서 영양 키워드가 must_have에서 제거되었는지 확인
  - 최종 결과에서 다이어트 식품이 상위에 있는지 확인

---

### 8. 덤벨 추천 시나리오 (상품 부족 케이스)

- **사용자 발화**: `"덤벨 추천해줘"`

- **기대 조건 생성** (AI 서버)
  - `product_category = "HEALTH_GOODS"`
  - `keyword = "덤벨"`
  - `must_have = ["덤벨"]`

- **기대 추천 결과** (백엔드)
  - 실제 덤벨 상품이 있으면 해당 상품이 1위
  - 덤벨이 없으면:
    - 관련 운동용품(손목 보호대, 리프팅 스트랩 등)이 대체 추천
    - 로그에 `keywordScore 우선 정렬 적용: candidates=N, popularityCount=0` 확인
    - 연관도가 높은 상품(덤벨 운동 시 사용하는 보호대 등)이 상위에 위치

- **검증 포인트**
  - 후보가 적을 때 `keywordScore 우선 정렬`이 적용되는지 확인
  - popularity 데이터가 없을 때 연관도 기준 정렬이 되는지 확인

---

### 9. 점수 기반 정렬 검증 시나리오

- **사용자 발화**: `"무릎 보호대 추천해줘"`

- **기대 동작**
  - 후보가 3개 이하이거나 popularity 데이터 비율이 30% 미만이면:
    - `keywordScore 우선 정렬` 적용
    - `keywordScore`가 높은 상품이 상위에 위치
  - 후보가 충분하고 popularity 데이터가 있으면:
    - popularity + 그룹 내 keywordScore 재정렬 적용
    - 인기 상품 중 연관도가 높은 상품이 상위에 위치

- **검증 포인트**
  - 로그에서 정렬 전략 선택 로그 확인
  - `keywordScore`가 높은 상품이 낮은 상품보다 상위에 있는지 확인

---

---

### 10. Goal 패널티 검증 시나리오 (2026-02-04 추가)

- **목적**: goal 기반 제외 키워드가 더 이상 하드 필터로 작동하지 않고, 점수 패널티로만 동작하는지 검증

- **테스트 케이스 A: 다이어트 목표 + 보호대**
  - **사용자 발화**: `"다이어트 중인데 무릎 보호대 추천해줘"` (goal=DIET)
  - **기대 동작**:
    - 무릎 보호대가 후보에서 **제거되지 않음** (하드 필터 비활성화)
    - 무릎 보호대가 결과에 포함되어야 함
    - 설명에 "벌크업", "체중 증가" 키워드가 있는 상품은 점수가 낮아짐

- **테스트 케이스 B: 벌크업 목표 + 다이어트 식품 검색**
  - **사용자 발화**: `"벌크업 중인데 다이어트 음식 뭐 있어?"` (goal=BULK_UP)
  - **기대 동작**:
    - "다이어트" 키워드가 있는 음식이 후보에서 **제거되지 않음**
    - 단, 점수 패널티(-20 ~ -35)가 적용되어 순위가 뒤로 밀림
    - 벌크업 관련 음식이 있으면 더 높은 순위에 위치

- **검증 포인트**
  - 로그에서 `goal 하드 필터 제거됨` 주석 확인
  - 로그에서 `goalPenalty` 적용 여부 (최종 추천 결과의 점수 확인)
  - goal과 상충하는 상품이 **결과에 포함되지만 순위가 낮은지** 확인

---

### QA 체크리스트

| 시나리오 | 입력 | 기대 1위 상품 유형 | 체크 |
|---------|------|-------------------|------|
| 하체 보호대 | "하체 운동할 건데 보호대 추천해줘" | 무릎/하체 보호대 (니랩, 니슬리브) | [ ] |
| 다이어트 음식 | "다이어트 중인데 음식 추천해줘" | 다이어트 식품 (저칼로리 음식, 다이어트 국수) | [ ] |
| 덤벨 | "덤벨 추천해줘" | 덤벨 (없으면 관련 운동용품) | [ ] |
| 무릎 보호대 | "무릎 보호대 추천해줘" | 무릎 보호대 (니랩, 니슬리브) | [ ] |
| 손목 스트랩 | "손목 아파서 스트랩 필요해" | 손목 스트랩/리프팅 스트랩 | [ ] |
| 다이어트 + 보호대 (goal 패널티) | "다이어트 중인데 무릎 보호대 추천해줘" | 무릎 보호대 (goal과 무관하게 결과 포함) | [ ] |
