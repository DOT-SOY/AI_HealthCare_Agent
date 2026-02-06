from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import asyncio
import os
import base64
from dotenv import load_dotenv

# 환경 변수 로드 (ai-server 폴더의 .env 파일)
import pathlib
env_path = pathlib.Path(__file__).parent / ".env"
load_dotenv(dotenv_path=env_path)

# 서비스 임포트
from services.intent_service import classify_intent
from services.chat_service import generate_ai_answer
from services.pain_advice_service import generate_pain_advice
from services.workout_feedback_service import generate_workout_feedback
from services.image_classification_service import get_image_classification_service
from services.routine_recommend_service import (
    recommend_exercises,
    recommend_for_split_day,
    get_alternatives_for_exercise,
    get_split_definitions,
)
from services.commerce import handle_commerce_recommend, state_machine, CommerceState
from services.embedding_service import load_embedding_model


@asynccontextmanager
async def lifespan(app: FastAPI):
    """앱 시작 시 임베딩 모델 로딩"""
    await asyncio.to_thread(load_embedding_model)
    yield

# Meal(Gemini) 추가
from services.meal_service import (
    analyze_food_image,
    lookup_food_nutrition,
    generate_meal_plan,
    generate_meal_plan_week,
    generate_meal_plan_month,
    generate_meal_plan_days,
    replan_meal_plan,
    pick_foods_for_macros,
    generate_meal_advice,
)
from services.meal_command_service import resolve_meal_command
from services.gemini_service import generate_json
from prompts.meal_vision_followup import SYSTEM_PROMPT as VISION_FOLLOWUP_SYSTEM_PROMPT, get_followup_prompt

app = FastAPI(title="GrowLog AI Server", lifespan=lifespan)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 프로덕션에서는 특정 도메인 지정
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Pydantic 모델
class ChatRequest(BaseModel):
    text: str
    session_id: Optional[str] = None
    context: Optional[str] = None  # 컨텍스트 (이전 대화)


class ChatResponse(BaseModel):
    intent: str
    action: str
    entities: Optional[Dict[str, Any]] = None
    ai_answer: str
    requires_db_check: bool = False


class PainAdviceRequest(BaseModel):
    body_part: str
    count: int
    note: Optional[str] = None


class PainAdviceResponse(BaseModel):
    body_part: str
    count: int
    level: str
    advice: str
    sources: Optional[List[Dict[str, Any]]] = None


class WorkoutFeedbackRequest(BaseModel):
    exercise_type: str
    total_reps: int
    duration_sec: int
    main_issue: str
    bad_posture_ratio: float


class WorkoutFeedbackResponse(BaseModel):
    feedback: str


class ImageClassificationResponse(BaseModel):
    type: str  # "inbody" or "food" or "unknown"
    confidence: float
    nearest_point_id: Optional[str] = None
    error: Optional[str] = None


class InbodyAnalyzeResponse(BaseModel):
    intent: str = "INBODY_ANALYSIS"
    message: str
    data: Optional[Dict[str, Any]] = None


class FoodAnalyzeResponse(BaseModel):
    intent: str = "FOOD_ANALYSIS"
    message: str
    data: Optional[Dict[str, Any]] = None


# --- Meal(Gemini) 요청 모델 ---
class AiMealRequest(BaseModel):
    requestType: str
    profile: Optional[Dict[str, Any]] = None
    goal: Optional[Dict[str, Any]] = None
    currentMeals: Optional[List[Dict[str, Any]]] = None
    userQuestion: Optional[str] = None
    foodImageBase64: Optional[str] = None


class AiMealVisionFollowupRequest(BaseModel):
    userText: str
    analyzedFood: Dict[str, Any]


class MealCommandRequest(BaseModel):
    text: str
    context: Optional[Dict[str, Any]] = None


class AiMealLookupRequest(BaseModel):
    foodName: str
    ragQueries: Optional[List[str]] = None


class RoutineRecommendRequest(BaseModel):
    """루틴 추천 요청: 타겟/배제 부위 또는 분할+요일, 또는 대체 운동 요청"""
    target_body_parts: Optional[List[str]] = None
    exclude_body_parts: Optional[List[str]] = None
    split_type: Optional[int] = None  # 2, 4, 5
    day_index: Optional[int] = None   # 0-based
    replace_exercise_name: Optional[str] = None
    exclude_exercise_names: Optional[List[str]] = None  # 이미 추천된 운동명 (중복 제외)
    limit: int = 10


class RoutineRecommendResponse(BaseModel):
    message: str
    exercises: List[Dict[str, Any]] = []
    alternatives: Optional[Dict[str, Any]] = None
    split_definitions: Optional[Dict[str, Any]] = None




# 엔드포인트
@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """의도 분류 및 기본 답변 생성"""
    # 컨텍스트가 있으면 프롬프트 앞에 추가
    if request.context:
        # 컨텍스트 + 현재 입력을 하나의 텍스트로 구성
        full_text = f"이전 대화:\n{request.context}\n\n현재 입력: {request.text}"
    else:
        full_text = request.text

    # 1. 의도 분류
    intent_result = await asyncio.to_thread(classify_intent, full_text)

    # 1. 의도 분류 (intent, action, entities, ai_answer 포함)
    intent_result = classify_intent(full_text)
    intent = intent_result.get("intent", "GENERAL_CHAT")
    action = intent_result.get("action", "CHAT")
    entities = intent_result.get("entities", {}) or {}
    ai_answer = intent_result.get("ai_answer") or ""

    # 2. ai_answer가 비어 있으면 기존 방식대로 답변 생성 (하위 호환)
    if not ai_answer.strip():
        ai_answer = await asyncio.to_thread(
            generate_ai_answer, request.text, intent, entities
        )

    # 3. DB 체크 필요 여부 플래그 (백엔드 오케스트레이션 참고용)
    requires_db_check = intent in ["PAIN_REPORT", "WORKOUT", "MEAL_QUERY", "BODY_QUERY", "DELIVERY_QUERY", "PRODUCT_RECOMMEND"]

    return ChatResponse(
        intent=intent,
        action=action,
        entities=entities,
        ai_answer=ai_answer,
        requires_db_check=requires_db_check
    )


@app.post("/pain/advice", response_model=PainAdviceResponse)
async def pain_advice(request: PainAdviceRequest):
    """통증 조언 제공 (RAG 기반)"""
    result = generate_pain_advice(
        body_part=request.body_part,
        count=request.count,
        note=request.note
    )
    
    return PainAdviceResponse(
        body_part=request.body_part,
        count=request.count,
        level=result["level"],
        advice=result["advice"],
        sources=result["sources"]
    )


@app.post("/workout/feedback", response_model=WorkoutFeedbackResponse)
async def workout_feedback(request: WorkoutFeedbackRequest):
    """운동 세션 피드백 생성"""
    feedback = generate_workout_feedback(
        exercise_type=request.exercise_type,
        total_reps=request.total_reps,
        duration_sec=request.duration_sec,
        main_issue=request.main_issue,
        bad_posture_ratio=request.bad_posture_ratio
    )
    
    return WorkoutFeedbackResponse(feedback=feedback)


@app.post("/image/classify", response_model=ImageClassificationResponse)
async def classify_image(file: UploadFile = File(...)):
    """이미지가 인바디인지 음식인지 분류"""
    try:
        image_bytes = await file.read()
        classification_service = get_image_classification_service()
        result = classification_service.classify_image(image_bytes)
        
        return ImageClassificationResponse(
            type=result.get("type", "unknown"),
            confidence=result.get("confidence", 0.0),
            nearest_point_id=result.get("nearest_point_id"),
            error=result.get("error")
        )
    except Exception as e:
        return ImageClassificationResponse(
            type="unknown",
            confidence=0.0,
            error=str(e)
        )


@app.post("/inbody/analyze", response_model=InbodyAnalyzeResponse)
async def analyze_inbody(file: UploadFile = File(...)):
    """인바디 사진 분석 (나중에 외부 AI 연결)"""
    # 일단 기본 응답만 반환
    return InbodyAnalyzeResponse(
        intent="INBODY_ANALYSIS",
        message="인바디 분석 준비 중입니다. 곧 연결될 예정입니다.",
        data=None
    )


@app.post("/food/analyze", response_model=FoodAnalyzeResponse)
async def analyze_food(file: UploadFile = File(...)):
    """음식 사진 분석 → Meal(Gemini) 비전 파이프라인과 연동"""
    # 1) 업로드된 이미지를 base64로 변환
    content: bytes = await file.read()
    if not content:
        return FoodAnalyzeResponse(
            intent="FOOD_ANALYSIS",
            message="이미지 데이터를 읽을 수 없습니다. 다시 시도해주세요.",
            data=None,
        )

    image_b64 = base64.b64encode(content).decode("utf-8")

    # 2) Meal 서비스의 비전 분석 로직 재사용
    analyzed_wrapper = analyze_food_image(image_b64)
    analyzed = (analyzed_wrapper or {}).get("analyzedFood") or {}

    food_name = analyzed.get("foodName") or "알 수 없음"
    cal = analyzed.get("calories") or analyzed.get("cal") or 0
    carbs = analyzed.get("carbs") or 0
    protein = analyzed.get("protein") or 0
    fat = analyzed.get("fat") or 0

    # 3) 전역 AI 코치에서 바로 보여줄 수 있는 한국어 메시지 구성
    message = (
        "이미지 분석 완료!\n\n"
        f"음식명: {food_name}\n"
        f"칼로리: {cal} kcal\n"
        f"탄수화물: {carbs} g\n"
        f"단백질: {protein} g\n"
        f"지방: {fat} g\n\n"
        "이 정보를 바탕으로 식단 기록이나 추천에 활용할 수 있습니다."
    )

    return FoodAnalyzeResponse(
        intent="FOOD_ANALYSIS",
        message=message,
        data={
            "foodName": food_name,
            "calories": cal,
            "carbs": carbs,
            "protein": protein,
            "fat": fat,
        },
    )


# --- Meal(Gemini) 엔드포인트 ---
@app.post("/api/meal/command")
async def meal_command(request: MealCommandRequest):
    """
    식단 전용 '명령 추론' 엔드포인트
    - 프론트/백엔드는 이 결과(JSON)를 기반으로 실제 DB 작업/비동기 작업을 수행합니다.
    - 실패해도 예외 대신 ASK_CLARIFY 형태로 복구합니다.
    """
    return resolve_meal_command(request.text, request.context)


@app.post("/api/meal/analyze")
async def meal_analyze(request: AiMealRequest):
    rt = (request.requestType or "").upper()

    if rt == "ANALYZE_IMAGE":
        if not request.foodImageBase64:
            return {
                "suggestedMeals": None,
                "analyzedFood": {"foodName": "알 수 없음", "calories": 0, "carbs": 0, "protein": 0, "fat": 0},
                "adviceComment": "foodImageBase64 is required for ANALYZE_IMAGE",
            }
        analyzed = analyze_food_image(request.foodImageBase64).get("analyzedFood")
        return {"suggestedMeals": None, "analyzedFood": analyzed, "adviceComment": None}

    if rt == "GENERATE":
        profile = request.profile or {}
        goal = request.goal or {}
        result = generate_meal_plan(profile, goal)
        return {"suggestedMeals": result.get("suggestedMeals"), "target": result.get("target"), "analyzedFood": None, "adviceComment": None}

    if rt == "GENERATE_WEEK":
        profile = request.profile or {}
        goal = request.goal or {}
        result = generate_meal_plan_week(profile, goal)
        return {"suggestedMeals": result.get("suggestedMeals"), "target": result.get("target"), "analyzedFood": None, "adviceComment": None}

    if rt == "GENERATE_MONTH":
        profile = request.profile or {}
        goal = request.goal or {}
        result = generate_meal_plan_month(profile, goal)
        return {"suggestedMeals": result.get("suggestedMeals"), "target": result.get("target"), "analyzedFood": None, "adviceComment": None}

    if rt == "GENERATE_DAYS":
        profile = request.profile or {}
        goal = request.goal or {}
        days = (goal or {}).get("periodDays") or (goal or {}).get("period_days") or 1
        result = generate_meal_plan_days(profile, goal, int(days))
        return {"suggestedMeals": result.get("suggestedMeals"), "target": result.get("target"), "analyzedFood": None, "adviceComment": None}

    if rt == "PICK_FOODS":
        goal = request.goal or {}
        picked = pick_foods_for_macros(
            {
                "targetCalories": goal.get("targetCalories") or 0,
                "targetCarbs": goal.get("targetCarbs") or 0,
                "targetProtein": goal.get("targetProtein") or 0,
                "targetFat": goal.get("targetFat") or 0,
            },
            exclude_keywords=goal.get("excludeKeywords") or goal.get("exclude_keywords") or [],
            exclude_food_names=goal.get("excludeFoodNames") or goal.get("exclude_food_names") or [],
            min_items=goal.get("minItems") or 1,
            max_items=goal.get("maxItems") or 3,
        )
        return {"suggestedMeals": picked, "analyzedFood": None, "adviceComment": None}

    if rt == "REPLAN":
        goal = request.goal or {}
        current_meals = request.currentMeals or []
        result = replan_meal_plan(goal, current_meals)
        return {"suggestedMeals": result.get("suggestedMeals"), "analyzedFood": None, "adviceComment": None}

    if rt == "ADVICE":
        current_meals = request.currentMeals or []
        result = generate_meal_advice(current_meals, request.userQuestion)
        return {"suggestedMeals": None, "analyzedFood": None, "adviceComment": result.get("adviceComment")}

    return {"suggestedMeals": None, "analyzedFood": None, "adviceComment": f"Unsupported requestType: {request.requestType}"}


@app.post("/api/meal/lookup")
async def meal_lookup(request: AiMealLookupRequest):
    resolved_name, macros = lookup_food_nutrition(request.foodName, extra_queries=request.ragQueries)
    return {"analyzedFood": {"foodName": resolved_name, **macros}}


@app.post("/api/meal/vision/followup")
async def meal_vision_followup(request: AiMealVisionFollowupRequest):
    """
    이미지 분석 결과를 사용자가 '추가/대체/취소' 등 자연어로 후속 지시할 때,
    어떤 행동을 해야 하는지 LLM이 판단해 JSON으로 반환한다.
    """
    raw = generate_json(
        system_prompt=VISION_FOLLOWUP_SYSTEM_PROMPT,
        user_prompt=get_followup_prompt(request.analyzedFood or {}, request.userText or ""),
        temperature=0.2,
        timeout_seconds=float(os.getenv("MEAL_VISION_FOLLOWUP_TIMEOUT_SECONDS", "12")),
    )
    op = (raw.get("operation") or "ASK").upper()
    mt = raw.get("mealTime")
    if isinstance(mt, str):
        mt_u = mt.upper()
        if mt_u in ("BREAKFAST", "LUNCH", "DINNER"):
            mt = mt_u
        else:
            mt = None
    else:
        mt = None
    reply = raw.get("assistantReply") or "추가할까요, 변경할까요?"
    return {"operation": op, "mealTime": mt, "assistantReply": reply}


class CommerceRecommendRequest(BaseModel):
    text: str
    session_id: str


class CommerceSessionCheckRequest(BaseModel):
    session_id: str


@app.post("/commerce/session/check")
async def commerce_session_check(request: CommerceSessionCheckRequest):
    """
    Commerce 세션 상태 확인 (SSOT).
    Redis에 해당 세션 키가 존재하면 in_flow=True, 없으면 False.
    """
    session = state_machine.get_session(request.session_id)
    in_flow = session is not None
    return {
        "in_flow": in_flow,
        "state": session.state.value if session and session.state else None
    }


@app.post("/commerce/recommend")
async def commerce_recommend(
    request: CommerceRecommendRequest,
    authorization: Optional[str] = Header(None, alias="Authorization")
):
    """
    Commerce 상품 추천 엔드포인트

    - 인증 토큰은 Authorization 헤더로만 전달
    - 상태머신 기반 대화 플로우 처리
    - Backend에서 내부 호출 시 authorization이 없을 수 있음 (선택적)
    """
    auth_token = authorization  # Header에서 받은 값 그대로 사용 (없으면 None)

    result = handle_commerce_recommend(
        text=request.text,
        session_id=request.session_id,
        auth_token=auth_token
    )

    return result


@app.post("/routine/recommend", response_model=RoutineRecommendResponse)
async def routine_recommend(request: RoutineRecommendRequest):
    """
    RAG 기반 루틴/대체 운동 추천.
    - target_body_parts + exclude_body_parts: 타겟 부위 유지, 위험 부위 배제
    - split_type + day_index: 2/4/5 분할의 해당 요일 부위로 추천
    - replace_exercise_name: 해당 운동의 대체 운동 (부상 위험 배제 적용)
    """
    exclude = request.exclude_body_parts or []

    if request.replace_exercise_name:
        alt = get_alternatives_for_exercise(request.replace_exercise_name, exclude)
        return RoutineRecommendResponse(
            message=f"'{request.replace_exercise_name}' 대체 운동 추천 (부상 위험 부위 배제 적용)",
            alternatives=alt,
        )

    if request.split_type is not None and request.day_index is not None:
        exercises = recommend_for_split_day(
            split_type=request.split_type,
            day_index=request.day_index,
            exclude_body_parts=exclude,
            limit=request.limit,
            exclude_exercise_names=request.exclude_exercise_names,
        )
        splits = get_split_definitions()
        return RoutineRecommendResponse(
            message=f"{request.split_type}분할 {request.day_index + 1}일차 추천 (위험 부위 배제 적용)",
            exercises=exercises,
            split_definitions=splits,
        )

    if request.target_body_parts:
        exercises = recommend_exercises(
            target_body_parts=request.target_body_parts,
            exclude_body_parts=exclude,
            limit=request.limit,
        )
        return RoutineRecommendResponse(
            message="타겟 부위 유지, 위험 부위 배제 기준 추천",
            exercises=exercises,
        )

    return RoutineRecommendResponse(
        message="target_body_parts 또는 split_type+day_index 또는 replace_exercise_name 중 하나를 지정해주세요.",
        exercises=[],
        split_definitions=get_split_definitions(),
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

