# AI 상품추천 흐름 문제점 분석

대화 로그 기반으로 발견된 AI 상품추천/커머스 플로우의 문제점을 정리한 문서입니다.

---

## 대화 로그 요약

| 사용자 발화 | 시스템 응답 | 문제 |
|------------|------------|------|
| 나 등운동할때 손이 너무 아파 | 배송지: 강민재 서울시 서초구 정의로 1 정도로 배송하시겠어요? | ❌ 주제 불일치 |
| 오늘 루틴 뭐였지? | 배송지: 강민재 서울시 서초구 정의로 1 정도로 배송하시겠어요? | ❌ 주제 불일치 |
| 나 등운동할때 손이 너무 아파 | 팔 통증이 걱정되시는군요. (휴식/냉찜질/스트레칭 등) | ❌ 부위 혼동(손↔팔) |
| 나 등운동할때 손이 너무 아픈데 뭘 사야할까 | 머슬업 밸런스 프로틴 (초코맛) 결제할까요? | ⚠️ 손 통증용 상품이 아닌 보충제 |
| 운동기구 추천해줘 | 구매하시겠어요? (예/아니오) | ❌ 새 추천 요청 무시 |
| 나 하체운동하는데 무릎이 아파서 보호대를 사야하는데 뭘 사야할까 | 킵고잉 파워 리프팅 스트랩 (블랙) 결제할까요? | ❌ 무릎 보호대가 아닌 리프팅 스트랩 |

---

## 1. CONFIRM_ADDRESS 상태에서 주제 이탈 감지 없음

**증상**
- "나 등운동할때 손이 너무 아파", "오늘 루틴 뭐였지?" 같은 **상품/배송과 무관한 발화**에도 계속 **배송지 확인 문구**만 반복됨.

**원인**
- `handle_confirm_address_state`는 사용자 발화(`text`)에 대해 **취소/긍정/부정/수취인명**만 판별함.
- **주제 이탈(오프토픽)** 또는 **새 상품 추천 요청** 여부를 보지 않고, 해당 없으면 무조건 `"배송지: {주소}로 배송하시겠어요?"`를 다시 보냄.

**개선 방향**
- CONFIRM_ADDRESS 진입 시 사용자 발화가
  - 상품/배송/결제와 무관하거나
  - 새 추천 요청(예: "운동기구 추천해줘", "다른 거 추천해줘")이면  
  세션을 RECOMMEND로 되돌리거나 세션 삭제 후 상위 라우터에 OFF_TOPIC 반환.
- 또는 상위(백엔드/프론트)에서 **글로벌 의도 분류**를 먼저 하고, commerce 세션이 있어도 intent가 WORKOUT/PAIN_REPORT/일반질문 등이면 commerce가 아닌 해당 도메인으로 라우팅하도록 변경.

---

## 2. CONFIRM_PRODUCT 상태에서 새 추천 요청 무시

**증상**
- 사용자가 "운동기구 추천해줘"라고 **새 추천을 요청**했는데, 시스템은 **"구매하시겠어요? (예/아니오)"**만 다시 물어봄.

**원인**
- `handle_confirm_product_state`는 **긍정/부정 키워드만** 보고, 그 외에는 모두 **애매한 응답**으로 간주해 동일 확인 문구만 재출력함.
- "운동기구 추천해줘", "다른 거 보여줘" 같은 **명시적 새 추천 요청**을 인지하지 못함.

**개선 방향**
- CONFIRM_PRODUCT에서 사용자 발화에 대해
  - `extract_commerce_intent_and_slots`로 intent·슬롯을 한 번 추출하고,
  - intent가 PRODUCT_RECOMMEND이고 "추천해줘", "뭐 살까", "다른 거" 등 **새 추천 요청** 패턴이면  
  상태를 RECOMMEND로 전환한 뒤 `handle_recommend_state`를 호출해 **새 추천 플로우**로 이어가기.
- 짧은 "응/아니"만 yes/no로 처리하고, 문장형 발화는 의도 분류 후 분기하도록 변경.

---

## 3. 통증 부위 혼동 (손 vs 팔)

**증상**
- 사용자: "**손**이 너무 아파" → 시스템: "**팔** 통증이 걱정되시는군요" 및 팔 기준 조언(팔꿈치 스트레칭 등).

**원인**
- 상위 의도 분류 또는 통증 조언 엔티티에 **body_part**가 BACK/CHEST/SHOULDER/**ARM**/CORE/… 등으로만 정의되어 있고, **HAND(손)**/WRIST(손목)가 없을 가능성이 큼.
- "손"이 ARM으로 매핑되거나, 통증 조언 프롬프트/ RAG가 손과 팔을 구분하지 않음.

**개선 방향**
- intent 분류의 `body_part`(또는 동일 역할 엔티티)에 **HAND, WRIST** 추가.
- 통증 조언 생성 시 **손/손목**과 **팔/어깨**를 구분해, 부위에 맞는 스트레칭·조언 문구 사용.
- "등운동할 때 손이 아프다" → 그립/손목 보강용 상품 추천과 연결할지 정책 정한 뒤, 상품 추천 플로우와 일관되게 맞추기.

---

## 4. 손 통증 문맥에서의 상품 추천 불일치

**증상**
- "나 등운동할때 손이 너무 아픈데 **뭘 사야할까**" → **프로틴(머슬업 밸런스 프로틴)** 추천.

**해석**
- 사용자는 **손/그립/손목 보호**용 상품(스트랩, 글러브, 손목밴드 등)을 기대했을 가능성이 높음.
- 현재는 goal/카테고리/키워드가 "벌크업 + SUPPLEMENT" 등으로 잡혀 프로틴이 나온 것으로 보임. **통증·부위 정보가 추천 조건에 반영되지 않음.**

**개선 방향**
- "등운동할 때 손이 아파서 뭘 사야 할지" 같은 발화에서
  - **증상/부위(손, 손목)**를 슬롯 또는 별도 엔티티로 추출하고,
  - product_category를 HEALTH_GOODS로, keyword를 "스트랩", "손목밴드", "그립" 등으로 보정하는 규칙 또는 LLM 가이드 추가.
- commerce_intent / commerce_recommendation 프롬프트에  
  "통증 부위·운동 종목이 언급되면, 그에 맞는 보호/보조 용품(HEALTH_GOODS)을 우선 고려"하도록 명시.

---

## 5. 무릎 보호대 요청 시 리프팅 스트랩 추천 (잘못된 상품 매칭)

**증상**
- "나 하체운동하는데 **무릎이** 너무 아파서 **보호대를** 사야하는데 뭘 사야할까"  
  → **킵고잉 파워 리프팅 스트랩**(블랙) 추천.

**원인**
- **keyword**가 "무릎 보호대", "보호대", "무릎" 등으로 제대로 넘어가지 않았거나,
- Backend `/api/products/recommend`에 무릎 보호대 상품이 없어 다른 HEALTH_GOODS(리프팅 스트랩)가 나왔거나,
- 추천 조건 생성 단계에서 keyword/ product_category가 누락·덮어쓰기 되었을 가능성.

**개선 방향**
- commerce_intent 프롬트에서 "보호대", "무릎", "무릎 보호대" 등이 명시되면 **keyword**에 반드시 반영하도록 예시 추가.
- commerce_recommendation(조건 생성)에서 **발화에 나온 구체적 상품 유형(무릎 보호대)**을 keyword/must_have에 유지하도록 규칙 강화.
- Backend 쪽에서 **keyword="무릎 보호대"** 또는 **product_category=HEALTH_GOODS + 무릎 관련 태그**로 검색했을 때, 무릎 보호대가 없으면 "해당 상품이 없습니다" 등으로 명시하고, **리프팅 스트랩을 대체 추천으로 넣지 않기**.

---

## 6. RECOMMEND 상태에서의 오프토픽 처리 (참고)

- 문서 `ai-commerce-conversation-test-scenarios.md`에는  
  "오늘 루틴 뭐였지?"처럼 **RECOMMEND 상태에서** 다른 도메인(루틴 질문)으로 바뀌면  
  **OFF_TOPIC + 세션 삭제** 후 상위에서 루틴 도메인으로 넘기는 시나리오가 정의되어 있음.
- 현재 문제는 **RECOMMEND가 아니라 CONFIRM_ADDRESS / CONFIRM_PRODUCT** 상태에서 들어오는 오프토픽·새 추천 요청이 처리되지 않는 점이므로, 위 1·2번과 연계해 같은 정책을 CONFIRM_* 상태에도 확장하는 것이 좋음.

---

## 요약 체크리스트

| # | 문제 | 상태 처리 | 조치 요약 |
|---|------|-----------|-----------|
| 1 | 배송지 확인 중 주제 이탈 시에도 배송지 문구 반복 | CONFIRM_ADDRESS | 오프토픽/새 추천 감지 후 RECOMMEND 전환 또는 OFF_TOPIC 반환 |
| 2 | 결제 확인 중 "운동기구 추천해줘" 등 새 추천 요청 무시 | CONFIRM_PRODUCT | 새 추천 요청 감지 시 RECOMMEND로 전환 후 handle_recommend_state 호출 |
| 3 | "손이 아파" → "팔 통증"으로 응답 | (통증 도메인) | body_part에 HAND/WRIST 추가, 손/팔 구분 반영 |
| 4 | 손 아픈데 뭘 사야 할까 → 프로틴 추천 | RECOMMEND | 통증·부위 기반 HEALTH_GOODS/키워드(스트랩, 손목 등) 반영 |
| 5 | 무릎 보호대 요청 → 리프팅 스트랩 추천 | RECOMMEND + Backend | keyword 추출 강화, 무릎 보호대 없을 때 대체 상품으로 스트랩 노출 금지 |

이 문서는 위 대화 로그와 코드(`commerce_orchestration_service`, `commerce_intent`, `commerce_recommendation`, intent_classification)를 기준으로 작성되었습니다.

---

## 적용된 수정 사항 (요약)

1. **CONFIRM_PRODUCT / CONFIRM_ADDRESS에서 intent 재분류**
   - 문장형 발화(길이 ≥5) 시 `classify_intent_top_level` 호출. 비커머스 도메인이면 세션 삭제 후 `OFF_TOPIC` + `intent`/`entities` 반환.
   - `extract_commerce_intent_and_slots`로 새 추천 요청이면 RECOMMEND로 전환 후 `handle_recommend_state` 호출.

2. **통증·부위·상품 유형 보존**
   - `intent_classification`: body_part에 HAND, WRIST, KNEE 추가.
   - `commerce_intent`: 통증·부위·"보호대" 언급 시 product_category=HEALTH_GOODS, keyword 보존 규칙 및 예시 추가.
   - `commerce_recommendation`: 추출된 keyword/product_category 덮어쓰지 말고 보존하도록 정책 명시.

3. **Backend keyword 0건 시 책임 분리**
   - `ProductRecommendationResponse`에 `conditionMatched`, `alternativeCandidates` 추가.
   - keyword 지정 시 검색 0건이면 메인 `products`는 비우고 `conditionMatched=false`, 참고용만 `alternativeCandidates`로 반환.
   - ai-server: `conditionMatched=false`일 때 `CONDITION_NO_MATCH` 메시지 및 `alternativeCandidates` 전달.
