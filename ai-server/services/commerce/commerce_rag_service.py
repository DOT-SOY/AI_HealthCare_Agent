"""
Commerce RAG 검색 서비스
- 임베딩 차원: embedding_service.EMBEDDING_DIM(384)와 Qdrant 컬렉션 일치 필수.
- 검색 실패 시 rag_hit=False, retrieved_chunks=0, error를 명시하여 조용히 넘어가지 않음.
- 이 모듈은 **상품을 직접 고르지 않고**, 추천 기준/정책/가이드 문서만 조회한다.
"""
import os
from typing import List, Dict, Any, Optional, TypedDict
from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue, MatchAny
from services.embedding_service import get_embedding, EMBEDDING_DIM

qdrant_client: Optional[QdrantClient] = None
QDRANT_COLLECTION_COMMERCE = os.getenv("QDRANT_COLLECTION_COMMERCE", "commerce_knowledge")

COMMERCE_POLICY_DOC_TYPES: List[str] = [
    "goal_playbook",
    "category_guide",
    "safety_policy",
]

try:
    qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
    qdrant_client = QdrantClient(url=qdrant_url)
    print(f"Qdrant 연결 성공: {qdrant_url}")
except Exception as e:
    print(f"Qdrant 연결 실패 (RAG 없이 동작): {e}")
    qdrant_client = None


class CommerceRagResult(TypedDict, total=False):
    results: List[Dict[str, Any]]
    rag_hit: bool
    retrieved_chunks: int
    error: Optional[str]


def search_commerce_rag(
    goal: Optional[str] = None,
    product_category: Optional[str] = None,
    doc_types: Optional[List[str]] = None,
    query_text: Optional[str] = None,
    limit: int = 10
) -> CommerceRagResult:
    """
    Commerce RAG 검색 (필터 포함).
    정책/가이드 문서만 대상. 실패 시 rag_hit=False, retrieved_chunks=0, error 반환.
    """
    empty_fail = lambda err: CommerceRagResult(
        results=[], rag_hit=False, retrieved_chunks=0, error=err
    )

    if not qdrant_client:
        print("[commerce_rag] rag_hit=false retrieved_chunks=0 error=qdrant_not_connected")
        return empty_fail("qdrant_not_connected")

    try:
        try:
            coll = qdrant_client.get_collection(QDRANT_COLLECTION_COMMERCE)
            params = getattr(getattr(coll, "config", None), "params", None)
            vectors = getattr(params, "vectors", None) if params else None
            size = None
            if vectors is not None:
                if hasattr(vectors, "size"):
                    size = vectors.size
                elif isinstance(vectors, dict) and vectors:
                    first = next(iter(vectors.values()), None)
                    size = getattr(first, "size", None) if first else None
            if size is not None and size != EMBEDDING_DIM:
                err = f"dimension_mismatch expected={EMBEDDING_DIM} got={size}"
                print(f"[commerce_rag] {err} -> rag_hit=false retrieved_chunks=0")
                return empty_fail(err)
        except Exception as e:
            err = f"collection_info_error: {e}"
            print(f"[commerce_rag] {err} -> rag_hit=false retrieved_chunks=0")
            return empty_fail(err)

        filter_conditions = []
        filter_conditions.append(
            FieldCondition(key="domain", match=MatchValue(value="commerce"))
        )
        if goal and goal != "ALL":
            filter_conditions.append(
                FieldCondition(key="goal", match=MatchAny(any=[goal, "ALL"]))
            )
        if product_category and product_category != "ALL":
            filter_conditions.append(
                FieldCondition(
                    key="product_category",
                    match=MatchAny(any=[product_category, "ALL"])
                )
            )
        effective_doc_types: Optional[List[str]] = None
        if doc_types:
            whitelisted = [d for d in doc_types if d in COMMERCE_POLICY_DOC_TYPES]
            if not whitelisted:
                effective_doc_types = COMMERCE_POLICY_DOC_TYPES
                print(f"[commerce_rag] doc_types filtered out, fallback to policy whitelist: {COMMERCE_POLICY_DOC_TYPES}")
            else:
                effective_doc_types = whitelisted
        else:
            effective_doc_types = COMMERCE_POLICY_DOC_TYPES

        if effective_doc_types:
            filter_conditions.append(
                FieldCondition(key="doc_type", match=MatchAny(any=effective_doc_types))
            )
        query_filter = Filter(must=filter_conditions) if filter_conditions else None

        query_vector = None
        if query_text:
            query_vector = get_embedding(query_text)
            if not query_vector:
                print("[commerce_rag] rag_hit=false retrieved_chunks=0 error=embedding_failed")
                return empty_fail("embedding_failed")

        if query_vector:
            search_results = qdrant_client.search(
                collection_name=QDRANT_COLLECTION_COMMERCE,
                query_vector=query_vector,
                query_filter=query_filter,
                limit=limit
            )
        else:
            scroll_results = qdrant_client.scroll(
                collection_name=QDRANT_COLLECTION_COMMERCE,
                scroll_filter=query_filter,
                limit=limit
            )
            points, _ = scroll_results

            class DummyResult:
                def __init__(self, payload, score):
                    self.payload = payload
                    self.score = score
            search_results = [
                DummyResult(p.payload, 1.0) for p in points if p.payload
            ]

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

        n = len(results)
        if n == 0:
            print("[commerce_rag] rag_hit=false retrieved_chunks=0 (no results)")
        else:
            print(f"[commerce_rag] rag_hit=true retrieved_chunks={n}")
        return CommerceRagResult(
            results=results,
            rag_hit=(n > 0),
            retrieved_chunks=n,
        )
    except Exception as e:
        err_msg = str(e)
        print(f"[commerce_rag] rag_hit=false retrieved_chunks=0 error=search_failed: {err_msg}")
        return empty_fail(f"search_failed: {err_msg}")
