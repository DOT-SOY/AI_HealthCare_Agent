---
name: ai-commerce-rag-redis-plan
overview: 기존 AI 상거래 추천/구매 플로우를 유지하면서, RAG를 추천 기준 전용으로 축소하고 Redis 세션 기반 Slot Filling/상태 관리를 강화하는 개편 계획.
todos:
  - id: clarify-rag-role
    content: RAG 사용 책임을 추천 기준/정책 컨텍스트로 명시하고 doc_type 화이트리스트를 고정한다.
    status: completed
  - id: separate-info-vs-product-lack
    content: handle_recommend_state와 Backend 호출부에서 정보 부족과 상품 부족을 명시적으로 구분하는 플래그와 분기 로직을 추가한다.
    status: completed
  - id: relax-recommend-loop
    content: 부정 응답 시 재추천 루프를 강제하지 않도록 상태 전이와 질문 메시지를 조정한다.
    status: completed
  - id: redis-slot-structure
    content: SessionData에 Slot Filling과 회원정보 슬롯을 분리해 추가하고 병합 규칙을 구현한다.
    status: completed
  - id: out-of-order-intents
    content: 주소/결제 관련 슬롯과 pending_action을 활용해 out-of-order 및 복합 발화를 안정적으로 처리한다.
    status: completed
  - id: rag-reuse-strategy
    content: 재추천 시 RAG 재호출 조건과 RecommendationCondition 재사용 전략을 정리하고 구현한다.
    status: completed
---

## AI Commerce 추천/구매 구조 개선 계획 (기존 설계 존중 개편안)

### 1. 현재 설계의 문제점 요약

#### 1-1. RAG 사용 방식

- **현재**: `generate_recommendation_condition`에서 RAG(`search_commerce_rag`)가 `goal`, `product_category`, `doc_type` 기반으로 정책 문서를 검색하고, 이 컨텍스트를 그대로 LLM 시스템 프롬프트에 주입하여 `RecommendationCondition`을 생성함. 실제 상품 조회는 별도 Backend `/api/products/recommend`에서 수행.
- **문제**:
- RAG 결과가 LLM 프롬프트 안에 섞여 있어, "정책/기준"과 "개별 상품/케이스"의 책임 경계가 코드 상에서 명확히 드러나지 않음.
- doc_type이 정책 위주이긴 하지만, 향후 다른 문서 타입이 섞일 경우에도 LLM이 그대로 사용하게 되어, 추천 기준이 아닌 정보(FAQ, 상세 설명 등)가 조건 생성에 섞일 위험이 있음.
- RAG 실패 시에도 단순한 fallback 문구로 LLM을 계속 호출하여, "RAG가 없는 상태에서 만들어진 기준"과 "정책을 충분히 반영한 기준"이 코드 상에서 구분되지 않음.
- **수정 방향**:
- RAG의 역할을 "추천 기준/정책 생성 전용 컨텍스트 제공"으로 문서화하고, 코드 상에서도 `doc_types`를 정책/가이드류(`goal_playbook`, `category_guide`, `safety_policy` 등)로 **고정·명시**.
- RAG 결과가 없을 때는 `RecommendationCondition` 안에 `user_profile_used=false`, `derived_constraints.reason="RAG_NOT_AVAILABLE"` 등으로 상태를 표현하여, 이후 단계(예: 재추천/조건 완화)에서 이를 구분 가능하게 함.
- RAG는 절대 상품 후보를 만들지 않고, **항상 Backend DB 조회(`call_backend_recommend`) 이전 단계에서만 호출**되도록 현재 위치를 유지하되, 함수/주석 수준에서 책임을 더 명확히 함.
- **기대 효과**:
- RAG가 정책/기준 레이어라는 것이 코드와 문서 모두에서 명확해져, 상품 추천/검색 책임이 DB/Backend에 고정됨.
- RAG 비정상/미사용 상태에서도 시스템 동작은 유지하면서, 품질 진단·로깅 시 어느 구간이 빠졌는지 쉽게 파악 가능.

#### 1-2. 추천 호출 타이밍

- **현재**:
- `handle_recommend_state`에서 매번 사용자 발화마다 `extract_commerce_intent_and_slots` → `generate_recommendation_condition` → Backend `/api/products/recommend`를 바로 호출.
- Slot/조건이 세션에 저장되긴 하지만, "정보 부족" vs "상품 부족"을 구분하여 후속 질문/완화 전략을 달리하는 구조는 아직 약함.
- **문제**:
- 사용자가 out-of-order로 정보를 주거나, 한 번에 여러 의도를 말할 때도 **항상 동일한 패턴으로 즉시 추천**을 시도하여, 불필요한 재호출/재추천 루프 발생.
- 최소 조건이 충족되지 않은 상태에서 추천을 호출한 뒤, 결과가 부족해도 그 원인이 "조건 부족"인지 "상품 부족"인지 구분하기 어려움.
- **수정 방향**:
- `extract_commerce_intent_and_slots` 결과와 세션의 기존 슬롯을 **먼저 병합한 후**, 최소 필수 슬롯 체크(예: goal, 대분류, 예산/카테고리 등) → 부족 시 질문만 반환하고 추천/DB 조회는 **보류**.
- Backend 추천을 호출한 후, 0건인 경우에는 "상품 부족"으로 태깅하여 세션에 `last_result_type=NO_PRODUCTS`를 기록하고, 조건 완화/카테고리 확장 등 fallback 로직을 태우도록 분리.
- **기대 효과**:
- 정보가 충분하지 않은 상태에서의 불필요한 추천 호출 감소.
- "정보 부족"과 "상품 부족"에 따른 서로 다른 UX/질문 전략을 구현할 수 있는 발판 마련.

#### 1-3. 상태 전이 구조

- **현재**:
- `CommerceStateMachine`이 Redis 기반 세션을 관리하며, 상태는 `RECOMMEND → CONFIRM_PRODUCT → ADD_TO_CART → CONFIRM_ADDRESS → PAYMENT_READY` 순으로 전이.
- `awaiting_since`는 질문 포함 응답 직전에만 갱신하도록 문서화 되었으나, 실제로는 "거절" 응답 후에도 곧바로 `RECOMMEND`로 전이하면서 재질문(재추천 유도 문구)을 던지는 구조가 있음.
- **문제**:
- `handle_confirm_product_state`에서 부정 응답 시:
- 바로 `RECOMMEND`로 전이 후, “다른 상품을 추천해드릴까요? 원하시는 조건을 말씀해주세요.” 질문이 나가면서 `awaiting_since` 갱신 → 사실상 즉시 재추천 루프로 들어갈 준비 상태가 됨.
- 상태 전이가 Redis 세션과 잘 연결되어 있지만, Slot/goal/address_mode 등 세부 컨텍스트가 상태(enum)와 뒤섞여 있고, 거절/취소의 의미가 세션 내 플래그로 충분히 표현되지 않음.
- **수정 방향**:
- 거절 응답(`is_negative`) 시에는 `state`는 `RECOMMEND`로 돌리되, **즉시 재추천을 요구하는 질문은 하지 않고**, `pending_action=None`, `last_reject_reason="PRODUCT_REJECT"` 등을 세션에 기록만 하는 형태로 변경.
- 이후 새로운 발화에서만 다시 추천 루프에 진입하도록 하여, "아니"가 곧바로 재추천 요청으로 해석되지 않도록 함.
- **기대 효과**:
- 사용자가 의도적으로 추천 루프를 끊고 싶을 때, 시스템이 질문을 강하게 유도하지 않아 피로도가 줄어듦.
- 상태 전이와 Redis 세션 컨텍스트가 명확히 분리되어, 추후 다양한 상태/플래그를 추가하기 쉬워짐.

#### 1-4. 회원정보(member_info_body) 반영 방식

- **현재**:
- ai-server 측에서는 `backend_client.get_user_profile`을 통해 키/몸무게, goal, 알레르기/avoid 등을 조회해 `generate_recommendation_condition`에 반영.
- Backend 상품 검색 레이어에서도 `ProductRecommendationRequest`에 goal/category/budget를 받아 필터링.
- **문제**:
- 회원정보 기반 조건(예: 알레르기, goal)은 LLM/RAG에서만 강하게 반영되고, 세션/Redis 슬롯 구조에서는 **명시적으로 분리된 필드**로 관리되지 않음.
- member_info에서 파생된 제약이 세션 재추천/재검색 시에도 항상 동일하게 반영되는지, 혹은 일회성으로만 사용되는지 코드만으로는 명확히 드러나지 않음.
- **수정 방향**:
- `SessionData`에 `member_goal`, `member_gender`, `member_height_cm`, `member_weight_kg`, `member_avoid` 등 **회원 기반 슬롯을 분리**하여 저장하고, `RecommendationCondition.derived_constraints`와 연결.
- LLM이 만들어낸 `avoid/must_have`와 회원정보에서 파생된 `avoid`를 각각 구분해 세션에 저장(예: `slot_avoid`, `profile_avoid`)하고, Backend 요청 시에는 합쳐서 전달.
- **기대 효과**:
- 세션이 끊겨도, 동일 회원 기준으로 다시 시작할 때 어떤 제약이 프로필에서 온 것인지, 발화에서 온 것인지 명확히 구분 가능.
- 추천 정책 변경 시에도, 회원정보 반영 로직만 교체해도 일관된 동작을 확보하기 쉬워짐.

---

### 2. 기존 설계를 유지하면서 수정해야 할 핵심 포인트

#### 2-1. 유지해야 할 부분

- **상태머신 구조**: `CommerceState` (RECOMMEND/CONFIRM_PRODUCT/ADD_TO_CART/CONFIRM_ADDRESS/PAYMENT_READY)와 Redis 기반 `CommerceStateMachine` 자체는 유지.
- **엔드포인트 계약**:
- `/commerce/recommend`와 `/commerce/session/check` 엔드포인트 시그니처는 유지.
- Backend `/api/products/recommend`, `/api/cart/ai/add-item`, `/api/orders/from-cart`, `/api/orders/{orderNo}/pay/ready`, `/api/member-addr-info/me`, `/api/members/me/profile` 호출 패턴도 기본적으로 유지.
- **LLM 기반 Intent/Slot 추출**: `extract_commerce_intent_and_slots`와 `generate_recommendation_condition`의 큰 틀(LLM 호출 및 JSON 스키마)은 그대로 사용.
- **RAG 인프라**: Qdrant 컬렉션, 임베딩 차원(384), `search_commerce_rag` 호출 구조는 유지.

#### 2-2. 수정/축소/분리해야 할 부분

- **RAG 책임 범위 축소**:
- `generate_recommendation_condition` 내부에서 `doc_types`를 정책/가이드 문서로 **고정**하고, 만약 다른 doc_type이 컬렉션에 추가되더라도 여기서는 사용하지 않도록 제약.
- RAG가 반환하는 내용은 오직 "추천 기준/정책 설명 텍스트"로만 조합하여 프롬프트에 넣고, 상품명/리뷰/FAQ 등은 사용하지 않도록 명시.
- **Slot/세션 분리 강화**:
- `SessionData`에 다음과 같은 필드를 명시적으로 추가 또는 정리: `goal_type`, `gender`, `height_cm`, `weight_kg`, `product_category`, `address_mode`, `pending_action`, `last_result_type`, `last_reject_reason`, `keyword`, `variant_option`, `recommendation_condition`.
- LLM/Intent는 "Slot 값 후보"만 반환하고, 실제로 어떤 값을 세션에 반영할지, 어떤 값을 보류할지 결정하는 책임은 오케스트레이션(`handle_recommend_state`)에 두도록 분리.
- **거절/재추천 루프 완화**:
- `handle_confirm_product_state`에서 부정 응답 시, "다른 상품을 추천해드릴까요?"와 같은 즉시 재추천 질문을 제거하고, 단순 상태 리셋 + 플래그 기록만 수행.
- 재추천은 **새 발화**(예: "다른 걸로 추천해줘")가 들어왔을 때만 새 조건 생성 → 추천을 트리거.

---

### 3. 추천 플로우 수정안

#### 3-0. 슬롯 정의 (결제 최소 / 추천용)

- **결제 최소 슬롯 (비어 있으면 반드시 질문해서 채워야 하는 값)**  
- `product_id`: 선택된 상품 ID (기존 `selected_product_id`와 1:1 매핑)  
- `variant_id`: 선택된 상품 옵션 ID (기존 `selected_variant_id`와 1:1 매핑)  
- `quantity`: 수량 (기본 1, 사용자 발화로 변경 가능)  
- `add_mode`: 배송지 모드 (`기존배송지` / `다른배송지` / `신규배송지` 등 enum으로 관리)  
- `add_id`: 실제 사용할 배송지 ID  

- **회원/신체 기반 추천 슬롯 (member / member_info_body에서 끌어다 채우는 값)**  
- `product_category`: 추천 대상 카테고리 (사용자 발화 + LLM 분석으로 채움)  
- `goal_type`: 운동/건강 목적 (`member_info_body.goal`)  
- `gender`: 성별 (`member.gender`)  
- `height_cm`: 키 (`member_info_body.heightCm`)  
- `weight_kg`: 몸무게 (`member_info_body.weightKg`)  

- **추가 추천 슬롯 (발화 + 프로필에서 병합해서 사용하는 값)**  
- `must_have`: \"단백질 높은\", \"무릎 지지\", \"민소매\" 같은 필수 조건 리스트  
- `avoid`: 알레르기/기피 성분 등 (프로필/발화에서 병합)  
- `sort_preference`: `가격낮은순` / `평점순` / `신상품` 등 정렬 선호 (사전 정의 enum으로 매핑)  

#### 3-1. Intent/Action 분리

- **현재**:
- `/chat`에서 intent/action을 구분하지만, commerce 플로우에서는 `extract_commerce_intent_and_slots`의 결과를 곧바로 추천으로 이어붙임.
- **수정 방향**:
- `extract_commerce_intent_and_slots`는 **의도(intent)**와 **슬롯(slot 후보)**만 책임지고, 실제 Action(추천/주소확인/결제진입 등)은 `handle_*_state`에서 세션과 합쳐서 결정.
- 예: "기본배송지로 보충제 결제할게" → `intent=PRODUCT_RECOMMEND`, `product_category=SUPPLEMENT`, `address_mode=DEFAULT`, `action_candidate="PAYMENT"` 등으로 slots만 채우고, 오케스트레이션이 현재 state와 세션 슬롯을 보고 "지금은 ADD_TO_CART → CONFIRM_ADDRESS → PAYMENT_READY로 간다"를 결정.

#### 3-2. Slot Filling → Redis 저장

- **현재**:
- 추천 시점에 `RecommendationCondition`과 일부 필드만 세션에 저장.
- **수정 방향**:
- `handle_recommend_state`에서:
- `extracted_slots`와 `session`의 기존 슬롯을 병합(새 발화가 우선)하여 `merged_slots` 생성.
- `merged_slots`를 기준으로 최소 필수 슬롯(예: goal/product_category/예산 또는 member_goal+카테고리 등)이 충족되었는지 판별.
- 충족된 슬롯과 회원정보 기반 슬롯을 모두 Redis 세션(`SessionData`)에 저장: `goal_type`, `product_category`, `budget`, `address_mode`, `keyword`, `variant_option`, `member_*` 등.
- Slot은 항상 Redis 세션이 SSOT가 되고, LLM은 매 발화마다 "현재 세션 슬롯 + 이번 발화"를 참고해 보정하는 방식으로 유지.

#### 3-3. RAG 호출 위치 조정

- **현재**:
- `generate_recommendation_condition` 내부에서, 프로필을 읽은 후 RAG → LLM 호출.
- **수정 방향**:
- **위치는 그대로 유지**하되, 호출 조건을 다음과 같이 명시:
- 필수 슬롯이 채워져 "DB 조회를 시도할 준비가 된 상태"에서만 RAG+LLM을 호출.
- 정보가 명백히 부족한 경우(예: goal도 없고 product_category도 ALL이고 예산도 없음)에는 RAG+LLM 호출을 스킵하고, **질문만 반환**하도록 `handle_recommend_state`가 분기.
- RAG 호출 시 `doc_types`는 정책/가이드 전용 리스트로 고정하고, 필요하다면 추가 정책 문서 타입만 whitelist로 확장.

#### 3-4. DB 조회 및 Fallback 흐름

- **현재**:
- `call_backend_recommend`에서 0건이거나 `products`가 비어 있으면 `NO_PRODUCTS_FOUND`로 반환.
- **수정 방향**:
- 0건일 때:
- 1차: `keyword`를 포함한 조건으로 조회.
- 2차 Fallback: Redis 세션에 기록된 동일 조건에서 keyword만 제외한 형태로 재조회.
- 그래도 0건이면 `last_result_type=NO_PRODUCTS`를 세션에 기록하고, LLM/정책 없이 고정 문구로 "해당 조건으로는 상품이 없습니다. 예산이나 카테고리를 조금 넓혀볼까요?" 정도의 한 번짜리 질문만 던짐.
- 이때 "상품 부족"임을 세션에 태깅하여, 다음 발화에서 조건 완화(예: budget_max 상향, product_category를 상위 카테고리로 확장)를 제안하는 방향으로 재추천.

---

### 4. Redis Slot Filling 도입 계획

#### 4-1. 세션 키 기준

- **현재**:
- `session_id`는 외부에서 주입되며, `commerce_state_machine`은 이를 그대로 Redis 키로 사용.
- **수정 방향**:
- Redis 키는 `commerce:session:{session_id}` 형태를 유지하되, Backend에서 `session_id` 생성 규칙(회원 단위 vs 프론트 세션 단위)을 명시 문서로 정의.
- `/commerce/session/check`는 이미 "키 존재 여부로만 in_flow 판단"하는 SSOT 구조이므로 그대로 유지.

#### 4-2. 슬롯 병합 규칙

- **수정 방향**:
- 새 발화에서 추출된 슬롯과 기존 세션 슬롯 병합 시 다음 규칙 적용:
- 새 발화에 동일 슬롯이 명시되면 **새 값이 우선**.
- 새 발화에 언급되지 않은 슬롯은 기존 세션 값을 유지.
- 거절/취소 등으로 플로우를 리셋할 때는 `pending_action`, `last_result_type`, `selected_product_id`, `selected_variant_id` 등을 초기화하되, 회원 기반 슬롯 및 장기 속성(`member_*`)은 유지.
- 병합된 슬롯을 항상 Redis에 저장하여, 이후 어떤 상태에서도 Slot Filling의 결과를 일관되게 참조.

#### 4-3. TTL 전략

- **현재**:
- `SESSION_TTL_SEC = 1800`(30분) + `awaiting_since` 기반 3분 무응답 만료.
- **수정 방향**:
- TTL 30분은 유지하되, Slot/세션 구조를 강화한 만큼 **세션 삭제 = 모든 슬롯/컨텍스트 삭제**를 명시.
- `awaiting_since`는 "질문이 포함된 응답"을 보낼 때만 갱신한다는 기존 규칙을 유지하면서, 질문 빈도를 줄여 실제 타임아웃과 사용자 피로를 균형 있게 맞춤.

#### 4-4. 기존 상태/세션 구조와의 연결 방식

- **수정 방향**:
- `SessionData` 타입(`services.commerce_types`)에 Redis Slot Filling 필드를 추가하고, 모든 상태 핸들러(`handle_recommend_state`, `handle_confirm_product_state`, `handle_confirm_address_state`, `handle_payment_ready_state`)에서 필요한 슬롯을 명시적으로 읽어 사용.
- 예: `CONFIRM_ADDRESS` 상태에서 `address_mode`(기본/신규/최근)를 세션 슬롯으로 관리하여, out-of-order로 먼저 배송지 언급이 들어온 경우에도 바로 적절한 흐름으로 분기.

---

### 5. RAG 관련 수정 계획

#### 5-1. doc_type 사용 방식 점검

- **현재**:
- `search_commerce_rag` 호출 시 `doc_types=["goal_playbook", "category_guide", "safety_policy"]`로 이미 정책 중심으로 명시.
- **수정 방향**:
- Qdrant 컬렉션 레벨에서 doc_type 설계를 정리하고, commerce RAG는 **위 3가지(또는 유사 정책 타입)만 사용**한다는 것을 문서/코드 주석으로 고정.
- 향후 상품 설명/FAQ 등 다른 doc_type이 필요하면, 별도 컬렉션 또는 별도 RAG 서비스로 분리하여 의도적으로 호출하도록 가이드.

#### 5-2. 추천 기준 외 문서 제거 또는 비활성화

- **수정 방향**:
- 현재 컬렉션이 정책 문서 위주라면, migration 시에도 doc_type이 정책 계열만 들어오도록 ETL/`setup_rag.py`에서 필터.
- 혹시라도 기존에 상품 설명/FAQ가 섞여 있다면, 해당 doc_type은 commerce RAG에서 조회되지 않도록 doc_type 필터로 차단.

#### 5-3. retrieval 범위/타이밍 조정

- **수정 방향**:
- RAG는 **추천 조건 생성 시 1회**만 호출하는 것을 원칙으로 하고, 재추천 시에도 이미 세션에 저장된 `RecommendationCondition`을 재사용하되, 슬롯이 의미 있게 변할 때만 RAG를 재호출.
- 예: goal이나 product_category가 크게 바뀔 때만 RAG를 다시 검색하고, 단순히 예산이나 keyword가 변하는 경우에는 이전 정책 컨텍스트를 그대로 재사용.

---

### 6. 구현 우선순위

#### 1단계: 최소 수정으로 효과가 큰 부분

- **정보 부족 vs 상품 부족 구분**:
- `handle_recommend_state`와 `call_backend_recommend`에 최소 슬롯 체크, 0건 fallback, `last_result_type` 세션 플래그 추가.
- **거절 응답 루프 완화**:
- `handle_confirm_product_state`에서 부정 응답 시 재추천 질문 제거, 플래그 기록만 수행.
- **RAG doc_type 고정 및 역할 명시**:
- `generate_recommendation_condition`와 RAG 서비스 주석에 RAG 책임 범위를 명확히 기술.

#### 2단계: 구조 개선

- **Redis Slot Filling 강화**:
- `SessionData`에 슬롯 필드 정리/추가, Slot 병합 규칙 구현, 회원정보 슬롯 분리.
- **Out-of-order/복합 발화 처리**:
- 주소/결제 관련 슬롯(`address_mode`, `pending_action`)을 도입하여, "기본배송지로 보충제 결제할게" 같은 발화를 상태머신 + 슬롯 병합으로 안정적으로 처리.
- **재추천/조건 완화 정책**:
- `NO_PRODUCTS`일 때 조건 완화 시나리오(카테고리 확장/예산 상향 등)를 간단한 규칙으로 우선 구현.

#### 3단계: 안정화/최적화

- **로그/모니터링 강화**:
- 세션별로 RAG 사용 여부, 추천 조건(요약), Backend 결과 개수, 거절/취소 횟수 등을 로깅/메트릭화.
- **LLM 프롬프트 정제**:
- RAG 컨텍스트와 사용자 슬롯/프로필을 더 간결하게 전달하여, 조건 생성의 일관성 및 비용 최적화.
- **세션 TTL/UX 튜닝**:
- 실제 트래픽 기반으로 3분/30분 기준이 적절한지 재조정하고, Slot Filling 구조를 활용해 "짧게 끊겼다가 다시 이어지는" 케이스도 자연스럽게 처리할 수 있도록 개선.