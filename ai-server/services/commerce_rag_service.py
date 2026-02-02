"""
Commerce RAG 검색 서비스
"""
import os
from typing import List, Dict, Any, Optional
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue, MatchAny
from services.embedding_service import get_embedding

# Qdrant 클라이언트 초기화
qdrant_client: Optional[QdrantClient] = None
QDRANT_COLLECTION_COMMERCE = os.getenv("QDRANT_COLLECTION_COMMERCE", "commerce_knowledge")

try:
    qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
    qdrant_client = QdrantClient(url=qdrant_url)
    print(f"Qdrant 연결 성공: {qdrant_url}")
except Exception as e:
    print(f"Qdrant 연결 실패 (RAG 없이 동작): {e}")
    qdrant_client = None


def search_commerce_rag(
    goal: Optional[str] = None,
    product_category: Optional[str] = None,
    doc_types: Optional[List[str]] = None,
    query_text: Optional[str] = None,
    limit: int = 10
) -> List[Dict[str, Any]]:
    """
    Commerce RAG 검색 (필터 포함)
    
    Args:
        goal: 운동 목적 (DIET, MAINTAIN, BULK_UP, ALL)
        product_category: 상품 카테고리 (FOOD, SUPPLEMENT, HEALTH_GOODS, CLOTHING, ETC, ALL)
        doc_types: 문서 타입 리스트 (goal_playbook, category_guide, safety_policy)
        query_text: 검색 쿼리 텍스트 (없으면 필터만 사용)
        limit: 결과 개수
    
    Returns:
        검색 결과 리스트
    """
    if not qdrant_client:
        return []
    
    try:
        # 필터 조건 생성
        filter_conditions = []
        
        # domain=commerce 필터 (필수)
        filter_conditions.append(
            FieldCondition(key="domain", match=MatchValue(value="commerce"))
        )
        
        # goal 필터 (goal 또는 ALL)
        if goal and goal != "ALL":
            filter_conditions.append(
                FieldCondition(
                    key="goal",
                    match=MatchAny(any=[goal, "ALL"])
                )
            )
        
        # product_category 필터 (product_category 또는 ALL)
        if product_category and product_category != "ALL":
            filter_conditions.append(
                FieldCondition(
                    key="product_category",
                    match=MatchAny(any=[product_category, "ALL"])
                )
            )
        
        # doc_type 필터
        if doc_types:
            filter_conditions.append(
                FieldCondition(
                    key="doc_type",
                    match=MatchAny(any=doc_types)
                )
            )
        
        # 필터 객체 생성
        query_filter = Filter(must=filter_conditions) if filter_conditions else None
        
        # 쿼리 임베딩 생성 (query_text가 있는 경우)
        query_vector = None
        if query_text:
            query_vector = get_embedding(query_text)
            if not query_vector:
                # 임베딩 생성 실패 시 필터만 사용
                query_vector = None
        
        # Qdrant 검색
        if query_vector:
            # 벡터 검색 + 필터
            search_results = qdrant_client.search(
                collection_name=QDRANT_COLLECTION_COMMERCE,
                query_vector=query_vector,
                query_filter=query_filter,
                limit=limit
            )
        else:
            # 필터만 사용 (스코어링 없음)
            scroll_results = qdrant_client.scroll(
                collection_name=QDRANT_COLLECTION_COMMERCE,
                scroll_filter=query_filter,
                limit=limit
            )
            # scroll 결과를 search 결과 형식으로 변환
            # scroll은 (points, next_page_offset) 튜플 반환
            points, _ = scroll_results
            search_results = []
            for point in points:
                if point.payload:
                    # 더미 스코어 (필터만 사용하는 경우)
                    class DummyResult:
                        def __init__(self, payload, score):
                            self.payload = payload
                            self.score = score
                    search_results.append(DummyResult(point.payload, 1.0))
        
        # 결과 변환
        results = []
        for result in search_results:
            if result.payload:
                results.append({
                    "doc_type": result.payload.get("doc_type", ""),
                    "goal": result.payload.get("goal", ""),
                    "product_category": result.payload.get("product_category", ""),
                    "title": result.payload.get("title", ""),
                    "content": result.payload.get("content", ""),
                    "section": result.payload.get("section", ""),
                    "tags": result.payload.get("tags", []),
                    "score": result.score
                })
        
        return results
    except Exception as e:
        print(f"Commerce RAG 검색 실패: {e}")
        return []

