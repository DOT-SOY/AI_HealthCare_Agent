"""
Vision AI 서비스
음식 사진 분석 (OpenAI Vision + RAG)
"""
from openai import AsyncOpenAI
import base64
from app.config import settings
from app.models.response import AnalyzedFood
from app.services.rag_service import RAGService
import json
import logging

logger = logging.getLogger(__name__)


class VisionService:
    """Vision AI 서비스"""
    
    def __init__(self):
        self.client = AsyncOpenAI(api_key=settings.openai_api_key)
        self.model = settings.openai_model
        self.rag_service = RAGService()
    
    async def analyze_food_image(self, base64_image: str) -> AnalyzedFood:
        """
        음식 사진 분석 (Vision + RAG)
        
        1. OpenAI Vision으로 음식명 추론
        2. RAG 검색 키워드 추출
        3. RAG로 영양성분 조회
        4. 최종 결과 반환
        """
        try:
            # Step 1: Vision으로 음식명 및 검색 키워드 추출
            prompt = """
            이 사진 속 음식을 분석해주세요.
            
            다음 형식의 JSON으로 응답해주세요:
            {
                "foodName": "음식명 (예: 양념치킨)",
                "searchKeywords": ["검색 키워드1", "검색 키워드2"]
            }
            
            searchKeywords는 RAG 검색에 사용할 키워드입니다.
            예: ["양념치킨", "치킨 1인분"]
            """
            
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:image/jpeg;base64,{base64_image}"
                                }
                            }
                        ]
                    }
                ],
                response_format={"type": "json_object"},
                temperature=0.7
            )
            
            vision_result = json.loads(response.choices[0].message.content)
            
            food_name = vision_result.get("foodName", "알 수 없는 음식")
            search_keywords = vision_result.get("searchKeywords", [food_name])
            
            # Step 2: RAG로 영양성분 검색
            nutrition = None
            for keyword in search_keywords:
                nutrition = await self.rag_service.search_nutrition(keyword)
                if nutrition:
                    break
            
            # Step 3: 최종 결과 구성
            if nutrition:
                return AnalyzedFood(
                    foodName=food_name,
                    calories=nutrition.get("calories"),
                    carbs=nutrition.get("carbs"),
                    protein=nutrition.get("protein"),
                    fat=nutrition.get("fat"),
                    confidence=0.9  # RAG 데이터가 있으면 높은 확신도
                )
            else:
                # RAG에서 못 찾으면 Vision 결과만 반환
                logger.warning(f"RAG에서 영양성분을 찾지 못함: {food_name}")
                return AnalyzedFood(
                    foodName=food_name,
                    confidence=0.7  # RAG 없으면 낮은 확신도
                )
        
        except Exception as e:
            logger.error(f"이미지 분석 실패: {e}", exc_info=True)
            raise

