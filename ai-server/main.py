from contextlib import asynccontextmanager
from fastapi import FastAPI, UploadFile, File, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import asyncio
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
from services.commerce import handle_commerce_recommend, state_machine, CommerceState
from services.embedding_service import load_embedding_model


@asynccontextmanager
async def lifespan(app: FastAPI):
    """앱 시작 시 임베딩 모델 1회 로딩 (요청 중 8초 지연 방지)"""
    await asyncio.to_thread(load_embedding_model)
    yield
    # shutdown: 필요 시 정리


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

    # 1. 의도 분류 (동기 블로킹 호출을 스레드 풀에서 실행해 이벤트 루프 블로킹 방지)
    intent_result = await asyncio.to_thread(classify_intent, full_text)
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
    """음식 사진 분석 (나중에 외부 AI 연결)"""
    # 일단 기본 응답만 반환
    return FoodAnalyzeResponse(
        intent="FOOD_ANALYSIS",
        message="음식 분석 준비 중입니다. 곧 연결될 예정입니다.",
        data=None
    )


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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

