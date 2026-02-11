# 로컬 파일의 식단 AI 구조 분석

## 전체 플로우

```
사용자 입력 (전역 모달 채팅창)
  ↓
Frontend: useAI.js → aiApi.sendMessage()
  ↓
Backend: AIGatewayController.handleAIChat()
  ↓
1. 의도 분류 (AIIntentService.classifyIntent())
   → "MEAL" 또는 "GENERAL_CHAT" (식단 컨텍스트 있으면 MEAL로 유지)
  ↓
2. MealCommandClient.resolveCommand()
   → AI-Server: /api/meal/command 호출
   → 자연어를 구조화된 명령(MealCommandResponseDto)으로 변환
  ↓
3. AIGatewayController.handleMeal()
   → 명령 타입(operation)에 따라 분기
   → MealService 메서드 호출
  ↓
4. MealService (비동기)
   → AI-Server: /api/meal/analyze 호출
   → 식단 생성/재정비/조언 등 수행
   → WebSocket으로 진행률 알림
```

## 1. AI-Server (Python) - 식단 관련 엔드포인트

### `/api/meal/command` (핵심)
- **역할**: 자연어를 구조화된 명령으로 변환
- **입력**: `{ text: string, context?: MealAiContextDto }`
- **출력**: `MealCommandResponseDto`
  - `operation`: GENERATE, GENERATE_OVERWRITE, GENERATE_FILL_MISSING, REPLAN, VISION_ADD, VISION_REPLACE, VISION_CANCEL, MEALTIME_COMPLETE_TOGGLE, MEALTIME_SKIP_TOGGLE, ITEM_COMPLETE_TOGGLE, ITEM_SKIP_TOGGLE, ASK_CLARIFY
  - `startDate`, `periodDays`, `goalType`, `targetDate`, `mealTime`, `foodName`, `alsoReplan`, `clarifyingQuestion`, `confidence`
- **구현**: `services/meal_command_service.py`
  - Fast-path: pending 기반 짧은 후속응답은 규칙으로 우선 처리
  - LLM: Gemini JSON 모드로 구조화된 명령 생성
  - 검증: Pydantic으로 타입/범위 강제

### `/api/meal/analyze`
- **역할**: 식단 생성/재정비/조언/이미지 분석
- **requestType**: ANALYZE_IMAGE, GENERATE, GENERATE_WEEK, GENERATE_MONTH, GENERATE_DAYS, REPLAN, ADVICE
- **구현**: `services/meal_service.py`
  - Gemini Vision: 음식 이미지 분석
  - Gemini JSON: 식단 생성/재정비/조언
  - Qdrant: 음식 영양정보 조회 (meal_foods 컬렉션)

### `/api/meal/lookup`
- **역할**: 음식명 기반 영양정보 조회
- **구현**: `services/meal_service.py` → `lookup_food_nutrition()`

### `/api/meal/vision/followup`
- **역할**: 이미지 분석 후 사용자 후속 지시 해석
- **구현**: `prompts/meal_vision_followup.py` + Gemini JSON

## 2. Backend (Java) - 식단 관련 컴포넌트

### AIGatewayController.handleMeal()
- **역할**: 식단 자연어 처리 메인 로직
- **플로우**:
  1. MealAiContextService로 대화 컨텍스트 저장/조회
  2. MealCommandClient로 자연어를 명령으로 변환
  3. 명령 타입에 따라 분기:
     - `REPLAN` → `mealService.asyncMealReplan()`
     - `VISION_ADD/REPLACE/CANCEL` → `mealService.applyVisionAdd/Replace()`
     - `MEALTIME_COMPLETE_TOGGLE` → `mealService.toggleMealTimeComplete()`
     - `MEALTIME_SKIP_TOGGLE` → `mealService.toggleMealTimeSkip()`
     - `ITEM_COMPLETE_TOGGLE/SKIP_TOGGLE` → `mealService.toggleItemByFoodName()`
     - `GENERATE` → `mealService.asyncGeneratePlanFromAiChat()`
     - `GENERATE_FILL_MISSING` → `mealService.asyncGeneratePlanFillMissingFromAiChat()`
     - `ASK_CLARIFY` → 사용자에게 질문 반환

### MealCommandClient
- **역할**: AI-Server의 `/api/meal/command` 호출
- **메서드**:
  - `resolveCommand(String userText)`
  - `resolveCommand(String userText, MealAiContextDto context)`

### MealAiContextService
- **역할**: Redis 기반 식단 도메인 대화 컨텍스트 관리
- **저장 정책**: 최근 3턴(6 메시지) + TTL 30분
- **구조**: `MealAiContextDto`
  - `history`: 최근 대화 메시지 리스트
  - `pending`: 대기 중 질문/선택 상태 (예: OVERLAP_STRATEGY, ASK_CLARIFY, VISION_FOLLOWUP)
  - `updatedAt`: 마지막 갱신 시간

### MealService (인터페이스)
- **추가 메서드들**:
  - `asyncGeneratePlanFromAiChat()`: 식단 생성
  - `asyncGeneratePlanFillMissingFromAiChat()`: 빈 날짜만 채우기
  - `applyVisionAdd()`: 이미지 분석 결과 추가
  - `applyVisionReplace()`: 이미지 분석 결과 대체
  - `toggleMealTimeComplete()`: 끼니 단위 완료 토글
  - `toggleMealTimeSkip()`: 끼니 단위 생략 토글
  - `toggleItemByFoodName()`: 항목 단위 완료/생략 토글

### MealServiceImpl
- **asyncGeneratePlanFromAiChat()**: 
  - MemberInfoBody에서 프로필 정보 조회
  - AiMealClient로 AI-Server 호출
  - 날짜별로 PLANNED 식단 교체 저장
  - WebSocket으로 진행률 알림
  - 목표치도 함께 저장

## 3. DTO

### MealCommandResponseDto
- AI-Server의 `/api/meal/command` 응답 매핑
- 모든 명령 타입의 공통 필드 포함

### MealAiContextDto
- 식단 도메인 대화 컨텍스트
- `Message`: role, content, at
- `Pending`: type, data, at

## 4. Frontend

### useAI.js
- `sendAIMessage()`: AI API 호출
- `response.intent === 'MEAL'`일 때 식단 처리

### useWebSocket.js
- `subscribeToMealGenerate()`: 식단 생성 진행률 알림
- `subscribeToMealReplan()`: 식단 재정비 알림
- `subscribeToMealVision()`: 이미지 분석 결과 알림
- `subscribeToMealChanged()`: 식단 변경 알림

### mealApi.js
- `generateMealPlan()`: (사용 안 함, 자연어로 처리)
- `analyzeVision()`: 이미지 분석
- `analyzeVisionFollowup()`: 이미지 분석 후속
- `requestReplan()`: 식단 재정비

## 핵심 설계 원칙

1. **자연어 → 구조화된 명령 변환**: LLM이 추론, 백엔드는 실행만
2. **멀티턴 대화 지원**: Redis 컨텍스트로 짧은 후속응답 처리
3. **Fast-path 최적화**: pending 기반 규칙 처리로 LLM 호출 최소화
4. **비동기 처리**: WebSocket으로 진행률 실시간 알림
5. **도메인 보호**: 식단 pending 중에는 다른 도메인으로 튀지 않도록 보호


