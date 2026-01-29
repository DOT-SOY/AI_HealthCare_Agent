"""
식단 관련 API 엔드포인트
"""
from fastapi import APIRouter, HTTPException
from app.models.request import AiMealRequest
from app.models.response import AiMealResponse
from app.services.vision_service import VisionService
from app.services.openai_service import OpenAIService
from app.services.rag_service import RAGService
import logging

logger = logging.getLogger(__name__)

router = APIRouter()

# 서비스 인스턴스 (의존성 주입 대신 싱글톤 패턴 사용)
vision_service = VisionService()
openai_service = OpenAIService()
rag_service = RAGService()


@router.post("/analyze", response_model=AiMealResponse)
async def analyze_request(request: AiMealRequest):
    """
    통합 AI 요청 처리 엔드포인트
    Java의 AiMealClient가 /api/meal/analyze로 요청을 보냄
    """
    try:
        request_type = request.requestType
        
        if request_type == "ANALYZE_IMAGE":
            return await handle_analyze_image(request)
        elif request_type == "ADVICE":
            return await handle_advice(request)
        elif request_type == "REPLAN":
            return await handle_replan(request)
        elif request_type == "GENERATE":
            return await handle_generate(request)
        else:
            raise HTTPException(
                status_code=400,
                detail=f"Unknown request type: {request_type}"
            )
    except Exception as e:
        logger.error(f"AI 요청 처리 실패: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


async def handle_analyze_image(request: AiMealRequest) -> AiMealResponse:
    """음식 사진 분석 (Vision + RAG)"""
    if not request.foodImageBase64:
        raise HTTPException(status_code=400, detail="foodImageBase64 is required")
    
    # Vision 서비스로 이미지 분석
    analyzed = await vision_service.analyze_food_image(request.foodImageBase64)
    
    return AiMealResponse(
        analyzedFood=analyzed
    )


async def handle_advice(request: AiMealRequest) -> AiMealResponse:
    """심층 영양 상담 (LLM + RAG)"""
    if not request.currentMeals:
        raise HTTPException(status_code=400, detail="currentMeals is required")
    
    # OpenAI 서비스로 상담 생성
    advice = await openai_service.generate_advice(request.currentMeals)
    
    return AiMealResponse(
        adviceComment=advice
    )


async def handle_replan(request: AiMealRequest) -> AiMealResponse:
    """식단 재분배 (LLM + RAG)"""
    if not request.goal:
        raise HTTPException(status_code=400, detail="goal is required")
    
    # OpenAI 서비스로 식단 재구성
    suggested_meals = await openai_service.replan_meals(
        goal=request.goal,
        current_meals=request.currentMeals or []
    )
    
    return AiMealResponse(
        suggestedMeals=suggested_meals
    )


async def handle_generate(request: AiMealRequest) -> AiMealResponse:
    """초기 식단 생성 (LLM + RAG)"""
    if not request.goal or not request.profile:
        raise HTTPException(status_code=400, detail="goal and profile are required")
    
    # OpenAI 서비스로 식단 생성
    suggested_meals = await openai_service.generate_meals(
        profile=request.profile,
        goal=request.goal
    )
    
    return AiMealResponse(
        suggestedMeals=suggested_meals
    )

