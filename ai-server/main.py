from fastapi import FastAPI, Header
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
from services.commerce_orchestration_service import handle_commerce_recommend
from services.commerce_state_machine import state_machine, CommerceState

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




# 엔드포인트
@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """의도 분류 및 기본 답변 생성"""
    # 1. 의도 분류 (intent, action, entities, ai_answer 포함)
    intent_result = classify_intent(request.text)
    intent = intent_result.get("intent", "GENERAL_CHAT")
    action = intent_result.get("action", "CHAT")
    entities = intent_result.get("entities", {}) or {}
    ai_answer = intent_result.get("ai_answer") or ""

    # 2. ai_answer가 비어 있으면 기존 방식대로 답변 생성 (하위 호환)
    if not ai_answer.strip():
        ai_answer = generate_ai_answer(request.text, intent, entities)

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


class CommerceRecommendRequest(BaseModel):
    text: str
    session_id: str


class CommerceSessionCheckRequest(BaseModel):
    session_id: str


@app.post("/commerce/session/check")
async def commerce_session_check(request: CommerceSessionCheckRequest):
    """
    Commerce 세션 상태 확인 엔드포인트
    
    - 세션이 존재하고 상품 추천 플로우 중인지 확인
    - RECOMMEND 상태가 아니면 플로우 중으로 간주
    """
    session = state_machine.get_session(request.session_id)
    
    if not session:
        return {
            "in_flow": False,
            "state": None
        }
    
    # RECOMMEND 상태가 아니면 상품 추천 플로우 중으로 간주
    in_flow = session.state != CommerceState.RECOMMEND
    
    return {
        "in_flow": in_flow,
        "state": session.state.value if session.state else None
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

