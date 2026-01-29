"""
Qdrant RAG 서비스
Vector DB를 통한 영양성분 검색 (OpenAI Embedding 사용)
"""
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from openai import AsyncOpenAI
from app.config import settings
import logging
from typing import Optional, Dict

logger = logging.getLogger(__name__)


class RAGService:
    """Qdrant RAG 서비스"""
    
    def __init__(self):
        # Qdrant 클라이언트 (URL 또는 host/port)
        if hasattr(settings, 'qdrant_url') and settings.qdrant_url:
            self.client = QdrantClient(url=settings.qdrant_url)
        else:
            self.client = QdrantClient(
                host=settings.qdrant_host,
                port=settings.qdrant_port
            )
        
        # 컬렉션 이름 (우선순위: qdrant_collection > qdrant_collection_name)
        self.collection_name = getattr(settings, 'qdrant_collection', None) or settings.qdrant_collection_name
        
        # OpenAI Embedding 클라이언트
        self.openai_client = AsyncOpenAI(api_key=settings.openai_api_key)
        self.embedding_model = settings.embedding_model
        
        self._ensure_collection()
    
    def _ensure_collection(self):
        """컬렉션이 없으면 생성"""
        try:
            collections = self.client.get_collections()
            collection_names = [c.name for c in collections.collections]
            
            if self.collection_name not in collection_names:
                # OpenAI text-embedding-3-small은 1536 차원
                self.client.create_collection(
                    collection_name=self.collection_name,
                    vectors_config=VectorParams(
                        size=1536,  # OpenAI text-embedding-3-small 벡터 크기
                        distance=Distance.COSINE
                    )
                )
                logger.info(f"Qdrant 컬렉션 생성: {self.collection_name}")
        except Exception as e:
            logger.error(f"Qdrant 컬렉션 확인/생성 실패: {e}", exc_info=True)
    
    async def _generate_embedding(self, text: str) -> list[float]:
        """OpenAI Embedding API로 텍스트를 벡터로 변환"""
        try:
            response = await self.openai_client.embeddings.create(
                model=self.embedding_model,
                input=text
            )
            return response.data[0].embedding
        except Exception as e:
            logger.error(f"임베딩 생성 실패: {e}", exc_info=True)
            raise
    
    async def search_nutrition(self, food_name: str) -> Optional[Dict]:
        """
        음식명으로 영양성분 검색
        
        Args:
            food_name: 검색할 음식명 (예: "양념치킨")
        
        Returns:
            영양성분 딕셔너리 또는 None
            {
                "calories": 250,
                "carbs": 10,
                "protein": 20,
                "fat": 15
            }
        """
        try:
            # 임베딩 생성
            query_vector = await self._generate_embedding(food_name)
            
            # Qdrant에서 검색
            search_results = self.client.search(
                collection_name=self.collection_name,
                query_vector=query_vector,
                limit=1
            )
            
            if search_results and len(search_results) > 0:
                result = search_results[0]
                payload = result.payload
                
                # 영양성분 정보 반환
                return {
                    "calories": payload.get("calories"),
                    "carbs": payload.get("carbs"),
                    "protein": payload.get("protein"),
                    "fat": payload.get("fat")
                }
            
            logger.warning(f"RAG에서 영양성분을 찾지 못함: {food_name}")
            return None
        
        except Exception as e:
            logger.error(f"RAG 검색 실패: {e}", exc_info=True)
            return None
    
    async def upload_nutrition_data(self, food_data: Dict):
        """
        영양성분 데이터를 Qdrant에 업로드
        
        Args:
            food_data: {
                "food_name": "양념치킨",
                "calories": 250,
                "carbs": 10,
                "protein": 20,
                "fat": 15,
                "serving_size": "1인분"
            }
        """
        try:
            # 임베딩 생성
            embedding = await self._generate_embedding(food_data["food_name"])
            
            # Qdrant에 업로드
            point = PointStruct(
                id=hash(food_data["food_name"]),
                vector=embedding,
                payload=food_data
            )
            
            self.client.upsert(
                collection_name=self.collection_name,
                points=[point]
            )
            
            logger.info(f"RAG 데이터 업로드 완료: {food_data['food_name']}")
        
        except Exception as e:
            logger.error(f"RAG 데이터 업로드 실패: {e}", exc_info=True)

