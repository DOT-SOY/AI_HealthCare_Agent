# AI 상품 추천-구매 로직 문제 분석 및 수정 방향

## 현상 요약

1. **상품 옵션을 제대로 찾지 못함** (예: "검은색" 요청 시 해당 옵션 미선택)
2. **구매(예)라고 해도 결제 페이지로 이동하지 않음**
3. **"레깅스"를 원해도 "나시"를 추천** — 상품명 인식 미반영

---

## 1. 결제 페이지로 이동하지 않는 문제

### 원인

**백엔드 오케스트레이션에서 “상품 추천 플로우 중인지”를 보지 않고, 의도(intent)만 보고 라우팅함.**

- 사용자가 "예", "응"이라고만 말하면 **의도 분류 결과가 `GENERAL_CHAT`**(또는 그에 가까운 값)으로 나올 가능성이 큼.
- 그러면 `AIChatOrchestrationServiceImpl`이 **commerce가 아니라 `generalChatService`**만 호출함.
- 따라서 **commerce 상태머신(CONFIRM_PRODUCT → ADD_TO_CART → CONFIRM_ADDRESS → PAYMENT_READY)이 한 번도 호출되지 않고**, `PAYMENT_READY` 응답이 나올 기회가 없음 → 프론트는 결제 페이지로 이동할 조건을 받지 못함.

관련 코드:

- `backend/.../AIChatOrchestrationServiceImpl.java`:  
  의도별 `switch`에서만 라우팅하고, **commerce 플로우 중 여부(`isInCommerceFlow`)를 먼저 조회하지 않음**.
- `CommerceChatServiceImpl.isInCommerceFlow(sessionId)`는 이미 구현되어 있으나 **오케스트레이션에서 사용되지 않음**.

### 수정 방향

- **요청 처리 시, 해당 멤버의 commerce 세션이 “플로우 중”이면 intent와 관계없이 commerce로 라우팅.**

구체적으로:

1. `AIChatOrchestrationServiceImpl.handleAIChat()` 상단(이미지 분기 다음, 텍스트 처리 시)에서:
   - `sessionId = "commerce_" + memberId` 로 세션 ID 생성.
   - `commerceChatService.isInCommerceFlow(sessionId)` 호출.
2. `true`이면 **의도 분류/switch 없이**  
   `commerceChatService.handleCommerceRecommend(classification, request.getText())` 호출  
   (이때 classification은 더미 또는 기존 로직으로 생성).
3. 이렇게 하면 "예", "응", "그래" 등 짧은 긍정 응답도 **항상 commerce 상태머신으로 전달**되어, CONFIRM_PRODUCT → ADD_TO_CART → CONFIRM_ADDRESS → PAYMENT_READY까지 진행되고, 결제 페이지 이동이 가능해짐.

---

## 2. "레깅스"를 원해도 "나시"를 추천하는 문제 (상품명 미반영)

### 원인

**상품명/키워드가 추천 파이프라인 어디에도 반영되지 않음.**

- **의도/슬롯 (ai-server)**  
  - `commerce_intent_service` / `commerce_intent` 프롬프트:  
    `goal`, `product_category`, `budget`, `avoid` 만 추출.  
    **`product_name` 또는 `keyword` 슬롯 없음.**
- **추천 조건 (ai-server)**  
  - `RecommendationCondition` / `commerce_recommendation`:  
    `product_category`는 있으나 **상품명/키워드 필드 없음.**
- **백엔드 추천 API**  
  - `ProductRecommendationRequest`:  
    `goal`, `productCategory`, `budgetMax`, `avoid`, `mustHave`, `priority` 만 있음.  
    **`keyword` / `productName` 없음.**
  - `ProductRecommendationServiceImpl.recommend()`:  
    `ProductSearchCondition`에 **keyword를 넣지 않음** →  
    카테고리(예: CLOTHING) + 가격/재고/avoid 등으로만 검색 →  
    **“레깅스”와 무관하게** 해당 카테고리 상품 중 점수 높은 순(예: 나시)이 1순위로 반환됨.

즉, "레깅스 검은색으로 사고싶어"에서 **"레깅스"는 어느 레이어에서도 검색/필터 조건으로 쓰이지 않음.**

### 수정 방향

- **전 구간에 “상품명/키워드”를 하나의 슬롯/파라미터로 추가하고, 백엔드 검색에 반영.**

1. **ai-server**
   - **의도/슬롯**  
     - `commerce_intent` 프롬프트 및 `commerce_intent_service` 응답 스키마에  
       `product_name` 또는 `keyword` 추가 (예: "레깅스", "나시", "보충제" 등).
   - **추천 조건**  
     - `RecommendationCondition` / `commerce_recommendation`에  
       `product_keyword`(또는 `product_name`) 필드 추가하고,  
       사용자 발화/슬롯에서 채워서 백엔드로 전달.

2. **백엔드**
   - **요청**  
     - `ProductRecommendationRequest`에 `keyword`(또는 `productName`) 추가.
   - **검색**  
     - `ProductRecommendationServiceImpl`에서  
       `ProductSearchCondition` 생성 시  
       `keyword`(및 필요 시 `searchType`, 예: "name")를 설정.
   - 기존 `ProductSearchImpl.keywordContains()`는 이미 keyword/searchType을 지원하므로,  
     **요청 DTO와 서비스에서만 keyword를 넘기면** 상품명 검색이 적용됨.

3. **정규화(선택)**  
   - "레깅스" → DB 상품명/태그와 매칭되도록,  
     동의어 매핑(레깅스 ↔ 상품명 일부)이나 검색 시 동의어 확장을 두면 추천 품질이 더 좋아짐.

---

## 3. 상품 옵션을 제대로 찾지 못하는 문제

### 원인

**사용자가 말한 옵션(예: "검은색")이 variant 선택 로직에 전혀 반영되지 않음.**

- **ai-server**  
  - `commerce_orchestration_service.handle_recommend_state()`에서  
    Backend에서 받은 `availableVariants` 중  
    **항상 첫 번째 variant만 선택**  
    (`selected_product.get("availableVariants", [{}])[0].get("variantId")`).
- 따라서 **색상/사이즈 등 사용자 발화와 variant 매칭하는 로직이 없음** → "검은색"을 요청해도 첫 번째 옵션이 담김.

### 수정 방향

- **사용자 발화(또는 슬롯)의 옵션 표현과 각 상품의 `availableVariants[].name`(optionText)을 매칭해, 해당 variant를 선택하도록 변경.**

1. **슬롯 확장 (선택)**  
   - 의도/슬롯에서 **색상/사이즈 등 옵션** 필드 추가  
     (예: `option_preference`: "검은색", "블랙", "L" 등).

2. **오케스트레이션**  
   - `handle_recommend_state()`에서:
     - Backend 응답의 `availableVariants` 리스트를 순회하며,
     - 각 variant의 `name`(또는 optionText)과  
       사용자 발화/슬롯의 옵션 키워드(정규화된 값)를 비교  
       (대소문자 무시, 공백 정규화, 동의어 매핑 예: "검은색"↔"블랙").
     - **매칭되는 첫 번째 variant**를 `selected_variant_id`로 설정하고,  
       매칭이 없을 때만 기존처럼 `availableVariants[0]` 사용.

3. **백엔드**  
   - `ProductRecommendationItem.ProductVariantSummary`에 이미 `name`(optionText)이 있으므로,  
     ai-server에서 이 이름으로 매칭만 하면 됨.  
   - 필요하면 옵션 타입(색상/사이즈)을 구분해 주는 필드를 추가하면,  
     "색상: 검은색"처럼 더 정확히 매칭할 수 있음.

---

## 수정 우선순위 제안

| 순서 | 항목 | 효과 | 난이도 |
|------|------|------|--------|
| 1 | **Commerce 플로우 우선 라우팅** (오케스트레이션에서 `isInCommerceFlow` 먼저 확인) | "예"/"응" 입력 시 결제 페이지까지 정상 진행 | 낮음 |
| 2 | **상품명/키워드 반영** (슬롯 → 조건 → Request → 검색) | "레깅스" 요청 시 레깅스 상품 우선 추천 | 중간 |
| 3 | **옵션 매칭** (발화/슬롯과 availableVariants.name 매칭) | "검은색" 등 옵션 정확히 반영 | 중간 |

---

## 참고: 응답 구조와 결제 페이지 이동

- 백엔드 `CommerceChatServiceImpl`은 ai-server의 commerce 응답 전체를  
  `AIChatResponse.data`에 넣어 반환함 (`data(commerceResponse)`).
- 프론트 `useAI.js`에서는  
  `response.data.state === 'PAYMENT_READY'` 및 `response.data.payment_ready`로  
  결제 페이지 이동을 하고 있음.  
  (여기서 `response`는 `aiApi.sendMessage`의 반환값 = 백엔드 응답 body이므로  
  `response.data`가 commerce payload임.)
- 따라서 **PAYMENT_READY가 한 번이라도 내려오면** 현재 구조로도 이동 가능함.  
  실제로 이동이 안 되는 이유는 **위 1번처럼 PAYMENT_READY가 나오기 전에 GENERAL_CHAT으로 빠지기 때문**이므로, 1번 수정이 선행되면 결제 페이지 이동 문제가 해소됨.
