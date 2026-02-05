from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import os
from dotenv import load_dotenv

# 환경 변수 로드 (ai-server 폴더의 .env 파일)
import pathlib
env_path = pathlib.Path(__file__).parent / '.env'
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

app = FastAPI(title="GrowLog AI Server")

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

    # 1. 의도 분류 (intent, action, entities, ai_answer 포함)
    intent_result = classify_intent(full_text)
    intent = intent_result.get("intent", "GENERAL_CHAT")
    action = intent_result.get("action", "CHAT")
    entities = intent_result.get("entities", {}) or {}
    ai_answer = intent_result.get("ai_answer") or ""

    # 2. ai_answer가 비어 있으면 기존 방식대로 답변 생성 (하위 호환)
    if not ai_answer.strip():
        ai_answer = generate_ai_answer(request.text, intent, entities)

    # 3. DB 체크 필요 여부 플래그 (백엔드 오케스트레이션 참고용)
    requires_db_check = intent in ["PAIN_REPORT", "WORKOUT", "MEAL_QUERY", "BODY_QUERY", "DELIVERY_QUERY"]

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
    """음식 사진 분석 (나중에 외부 AI 연결)"""
    # 일단 기본 응답만 반환
    return FoodAnalyzeResponse(
        intent="FOOD_ANALYSIS",
        message="음식 분석 준비 중입니다. 곧 연결될 예정입니다.",
        data=None
    )


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

