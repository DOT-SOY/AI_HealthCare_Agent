# Commerce 세션 Redis 이전·가드 + 상품명/키워드·옵션 매칭 (통합 플랜)

## 전체 범위

1. **Part 1–3**: 오케스트레이션 가드, Redis 세션 저장소, awaiting_since/만료 체크
2. **Part 4**: 상품명/키워드 반영 (슬롯 → 추천 조건 → Request → 검색)
3. **Part 5**: 옵션 매칭 (발화/슬롯 ↔ availableVariants.name)
4. **본문**: 아래 “추가 반영 조건”에 따른 계약·정책·규칙 명시

---

## 추가 반영 조건 (계약·정책·규칙)

### 1. /commerce/session/check — Redis 세션 키 존재만으로 판단 (SSOT)

- **단일 진실 원천(SSOT)**: “commerce 플로우 중 여부”는 **Redis에 해당 세션 키가 존재하는지**로만 판단한다.
- **동작**:
  - `GET commerce:session:{session_id}` 결과가 존재하면 `in_flow: true`, 없으면 `in_flow: false`.
  - 기존처럼 “state != RECOMMEND” 같은 추가 조건은 사용하지 않는다. 키 존재 = 플로우 중.
- **구현**: ai-server `commerce_session_check` 엔드포인트에서 Redis 스토어 `get(session_id)`만 호출하고, `session is not None` → `in_flow: True`, `None` → `in_flow: False`, `state`는 선택적으로 응답에 포함 가능.

---

### 2. handleCommerceRecommendBySession — sessionId 명시 수신

- **형태**: Backend에서 “세션 연속” 요청 시 **sessionId를 명시적으로 인자로 받는 형태**를 고려한다.
- **선택지**:
  - `AIChatResponse handleCommerceRecommendBySession(String userText, String sessionId);`
  - 또는 `handleCommerceRecommendBySession(String sessionId, String userText);`
- **효과**: 오케스트레이션에서 memberId → sessionId를 한 번만 계산하고, commerce 서비스는 세션 ID를 받기만 하면 되어 테스트·재사용이 쉬워진다. (내부에서 CurrentMemberService로 sessionId를 만드는 대안도 유지 가능하나, 명시 수신을 권장.)

---

### 3. SESSION_EXPIRED 응답 계약 및 Backend 처리 방침

- **계약 (ai-server → Backend/클라이언트)**:
  - 3분 무응답 또는 30분 TTL 등으로 세션을 종료한 경우, commerce 응답에 다음을 포함한다.
    - `"error": "SESSION_EXPIRED"`
    - `"state": "RECOMMEND"` (또는 null)
    - `"message": "세션이 만료되었습니다. 다시 시작해주세요."` (고정 문구 권장)
- **Backend 처리 방침**:
  - Commerce 응답을 그대로 클라이언트에 전달한다.
  - 필요 시 Backend에서 `error == "SESSION_EXPIRED"`인 경우 Redis 등에 저장한 “commerce active” 플래그를 삭제하여, 다음 요청에서 의도 분류로 진입하도록 한다.
  - 클라이언트는 `SESSION_EXPIRED` 시 안내 메시지 표시 후, 새로 “상품 추천” 요청을 하면 일반 플로우로 시작하도록 유도.

---

### 4. awaiting_since 세팅 조건 — “질문 포함 응답일 때만” 명문화

- **규칙**: `awaiting_since`는 **AI가 “사용자 응답을 기다리는 질문”을 포함한 응답을 보내는 순간**에만 갱신한다.
- **적용**:
  - “구매하시겠어요?”, “배송지: … 로 배송하시겠어요?”, “다른 상품을 추천해드릴까요?” 등 **질문이 포함된 응답**을 반환하기 직전에만 `update_session(..., awaiting_since=now)` 호출.
  - 단순 전이 메시지(예: “장바구니에 담았습니다.” 직후 다음 단계로 넘어가는 경우), 에러 메시지, 정보만 전달하는 응답에는 `awaiting_since`를 세팅하지 않거나, 기존 값을 유지한다.
- **문서화**: commerce 오케스트레이션 주석/문서에 “awaiting_since는 질문 포함 응답일 때만 갱신”으로 명시.

---

### 5. keyword 우선순위 및 0건 결과 fallback 정책

- **우선순위**:
  - 추천 조건에 `keyword`가 있으면 Backend 검색 시 **keyword 조건을 반드시 적용**한다 (상품명/설명 등 검색).
  - goal, product_category, budget 등과 **AND**로 결합하며, keyword는 “포함 검색”으로 동작한다.
- **0건 결과 fallback**:
  - keyword 적용 결과 0건이면:
    - **1안**: keyword 없이 동일한 goal/category/budget 등으로 재검색한 결과를 반환 (fallback).
    - **2안**: 0건 그대로 반환하고, ai-server에서 “해당 키워드로 찾은 상품이 없어요. 다른 조건으로 찾아볼까요?” 등 안내 메시지 반환.
  - 플랜에서는 **1안(fallback 재검색)** 을 기본으로 하고, 필요 시 2안으로 전환 가능하도록 명시.

---

### 6. keyword 정규화 — 동의어/한영/띄어쓰기 최소 규칙

- **목적**: “레깅스”, “레깅스”, “leggings” 등이 동일하게 검색되도록 최소한의 정규화를 적용한다.
- **최소 규칙**:
  - **띄어쓰기**: 앞뒤 공백 trim, 연속 공백을 하나로 치환 (또는 검색 시 LIKE/contains에서 공백 무시 정책 일치).
  - **한영 통일 (선택)**: 자주 쓰는 단어에 대해 한↔영 매핑 테이블을 두고 검색 전에 치환 (예: “레깅스” ↔ “leggings”). 적용 범위는 상품 도메인에서 자주 나오는 키워드만 우선.
  - **동의어 (선택)**: “운동복 바지” → “레깅스” 등 동의어 확장은 별도 테이블/설정으로 관리하고, 검색 쿼리 전에 1:1 치환. 초기에는 최소한만 적용.
- **적용 위치**: ai-server에서 조건 생성 시 keyword 정규화 후 Backend로 전달하거나, Backend 검색 레이어에서 정규화 후 쿼리. 한 곳에서만 수행해 SSOT 유지.

---

### 7. 옵션 매칭 — 복수 토큰, 토큰 경계, 재고 우선, 실패 시 UX

- **복수 토큰 처리**: 사용자 발화/슬롯에 “검은색 L”처럼 여러 옵션이 나올 수 있으면, 토큰으로 분리(공백/쉼표 등)한 뒤 각 토큰을 variant name과 매칭한다. 예: “검은색”, “L” → 색상 variant + 사이즈 variant가 있으면 둘 다 만족하는 variant 우선, 없으면 단일 토큰 매칭으로 fallback.
- **토큰 경계 매칭**: variant.name과 비교 시 **단어 경계**를 고려한다. “검정”이면 “검정색”과 매칭 가능하되, 부분 문자열이 과도하게 짧지 않도록 최소 길이(예: 2자) 또는 “포함” 규칙을 명시. (예: option_keyword가 variant.name에 포함되거나, 정규화 후 동일.)
- **재고 우선**: 매칭된 variant가 여러 개면 **재고 있는 것(stockQty > 0)을 우선** 선택하고, 동일하면 첫 번째.
- **실패 시 UX 정책**: 매칭되는 variant가 없으면 **기존처럼 첫 번째 availableVariant**를 사용하고, 응답 메시지에서 “원하시는 옵션(검은색)은 현재 준비되지 않아 기본 옵션으로 안내드립니다.” 등 한 줄 안내를 포함할지 결정. 플랜에서는 “첫 번째 variant 사용 + 선택적 안내 문구”로 명시.

---

### 8. 슬롯/키워드/옵션의 세션 저장 및 생애주기

- **저장**: 추출된 슬롯(goal, product_category, budget, avoid, **keyword**, **variant_option**)과 최종 **RecommendationCondition**(keyword 포함)은 이미 `session.recommendation_condition`(및 관련 필드)으로 세션에 저장된다. Redis 세션 스키마에 이 필드들이 포함되므로, “세션 저장”은 기존 상태머신 세션 저장과 동일한 생애주기를 따른다.
- **생애주기**:
  - **세션 생성**: RECOMMEND 상태 진입 시, 슬롯/조건은 비어 있거나 첫 요청으로 채워짐.
  - **세션 갱신**: RECOMMEND 상태에서 새 추천 요청 시 recommendation_condition(및 keyword 등) 갱신. CONFIRM_PRODUCT 등 다른 상태에서는 필요 시 참조만 하고, “재추천” 시에만 조건을 다시 채움.
  - **세션 삭제**: 3분 만료, 30분 TTL, 사용자 취소 시 세션 삭제되며 슬롯/키워드/옵션도 함께 삭제.
- **명시**: 설계서/주석에 “슬롯·keyword·variant_option은 세션(Redis)에 recommendation_condition 등으로 저장되며, 세션 TTL/만료/삭제와 동일한 생애주기를 가진다”고 적어 둔다.

---

### 9. DTO 기본값 및 호환성 처리

- **Backend ProductRecommendationRequest**:
  - 새 필드 `keyword`, `searchType`은 **optional**로 두고, null/빈 값이면 기존과 동일하게 “키워드 검색 없음”으로 동작하도록 한다.
  - `searchType` 기본값: null이면 `"all"` (상품명+설명 모두 검색). 기존 클라이언트는 필드를 보내지 않아도 동작해야 한다.
- **ai-server RecommendationCondition / 슬롯**:
  - `keyword`, `variant_option`은 없으면 null/빈 문자열. `from_dict`/직렬화 시 키가 없어도 기본값 적용.
  - Backend로 보내는 request_body에 keyword가 null이면 키를 생략하거나 `"keyword": null`로 보내고, Backend는 null/미존재 시 검색 조건에서 제외.
- **응답 DTO**: 기존 필드 유지, 새 필드 추가 시 optional로 두어 기존 클라이언트와의 호환성을 유지한다.

---

## Part 1–3 요약 (기존 플랜 유지)

- **Backend**: AIChatOrchestrationServiceImpl에서 텍스트 처리 직후 commerce 가드. `isInCommerceFlow(sessionId)` 후 true이면 `handleCommerceRecommendBySession(sessionId, userText)` 호출. CommerceChatService에 `handleCommerceRecommendBySession(String sessionId, String userText)` (또는 (userText, sessionId)) 추가.
- **ai-server**: Redis 세션 스토어, state_machine Redis 기반 교체, SessionData.awaiting_since, 만료 체크는 handle_commerce_recommend 진입 직후만, awaiting_since는 “질문 포함 응답일 때만” 갱신. 락은 우선 생략.
- **session/check**: Redis 키 존재 여부만으로 in_flow 판단 (SSOT).
- **SESSION_EXPIRED**: 응답 계약 및 Backend 처리 방침은 위 3번 참조.

---

## Part 4: 상품명/키워드 반영

- 슬롯 `keyword` 추가 → RecommendationCondition.keyword → call_backend_recommend 시 request_body에 keyword 전달 (null이면 생략 또는 null).
- Backend ProductRecommendationRequest에 keyword, searchType 추가 (기본값/호환성 9번 참조). ProductRecommendationServiceImpl에서 ProductSearchCondition에 keyword, searchType 설정.
- keyword 우선순위·0건 fallback은 5번, 정규화는 6번 참조.

---

## Part 5: 옵션 매칭

- 슬롯 `variant_option` 추가. 1순위 상품 선택 시 availableVariants와 매칭(복수 토큰, 토큰 경계, 재고 우선, 실패 시 첫 번째 variant + 선택적 안내) 후 selected_variant_id 설정.
- 세션 저장/생애주기는 8번 참조.

---

## 수정 파일 목록 (참고)

| 구분 | 파일 | 변경 요약 |
|------|------|------------|
| Backend | AIChatOrchestrationServiceImpl | commerce 가드, handleCommerceRecommendBySession(sessionId, userText) 호출 |
| Backend | CommerceChatService / Impl | handleCommerceRecommendBySession(String sessionId, String userText) |
| Backend | ProductRecommendationRequest | keyword, searchType (optional, 호환성) |
| Backend | ProductRecommendationServiceImpl | keyword/searchType 반영, 0건 fallback(키워드 제외 재검색) |
| ai-server | commerce_session_store, state_machine | Redis SSOT, session/check는 키 존재만 |
| ai-server | commerce_orchestration_service | 만료 체크 위치, awaiting_since “질문 포함 응답일 때만”, SESSION_EXPIRED 계약, keyword/옵션 매칭 로직 |
| ai-server | commerce_intent, recommendation_schema, commerce_recommendation | keyword, variant_option 슬롯/조건, 정규화(최소 규칙) |
