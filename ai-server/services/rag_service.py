"""
RAG 검색 서비스
"""
import os
from typing import List, Dict, Any, Optional
from qdrant_client import QdrantClient
from services.embedding_service import get_embedding

# Qdrant 클라이언트 초기화
qdrant_client: Optional[QdrantClient] = None
QDRANT_COLLECTION = os.getenv("QDRANT_COLLECTION", "exercise_knowledge")

try:
    qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
    qdrant_client = QdrantClient(url=qdrant_url)
    print(f"Qdrant 연결 성공: {qdrant_url}")
except Exception as e:
    print(f"Qdrant 연결 실패 (RAG 없이 동작): {e}")
    qdrant_client = None


def search_rag(query: str, limit: int = 5) -> List[Dict[str, Any]]:
    """RAG 검색"""
    if not qdrant_client:
        return []
    
    try:
        # 쿼리 임베딩 생성
        query_embedding = get_embedding(query)
        if not query_embedding:
            return []
        
        # Qdrant 검색
        search_results = qdrant_client.search(
            collection_name=QDRANT_COLLECTION,
            query_vector=query_embedding,
            limit=limit
        )
        
        # 결과 변환
        results = []
        for result in search_results:
            if result.payload:
                results.append({
                    "category": result.payload.get("category", ""),
                    "title": result.payload.get("title", ""),
                    "content": result.payload.get("content", ""),
                    "score": result.score
                })
        
        return results
    except Exception as e:
        print(f"RAG 검색 실패: {e}")
        return []


