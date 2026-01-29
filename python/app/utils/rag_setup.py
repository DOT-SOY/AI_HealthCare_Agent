"""
RAG 데이터 벡터화 및 Qdrant 업로드 스크립트
USDA FoodData Central, 식약처 API 데이터 수집 및 벡터화
"""
import asyncio
from app.services.rag_service import RAGService
import logging

logger = logging.getLogger(__name__)


async def setup_rag_data():
    """
    RAG 데이터 수집 및 벡터화
    프로젝트 루트에서 실행: python setup_rag.py
    """
    rag_service = RAGService()
    
    # TODO: USDA FoodData Central API 호출
    # TODO: 식약처 API 호출
    # TODO: 데이터 벡터화 및 Qdrant 업로드
    
    logger.info("RAG 데이터 설정 완료")


if __name__ == "__main__":
    asyncio.run(setup_rag_data())

