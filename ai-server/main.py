from fastapi import FastAPI
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
    requires_db_check = intent in ["PAIN_REPORT", "WORKOUT"]

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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

