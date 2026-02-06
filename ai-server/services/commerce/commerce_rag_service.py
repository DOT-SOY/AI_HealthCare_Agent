"""Commerce RAG: 추천 정책/가이드 문서만 조회 (상품 미포함)."""
import json
import os
from typing import List, Dict, Any, Optional, TypedDict

from qdrant_client import QdrantClient
from qdrant_client.models import Filter, FieldCondition, MatchValue, MatchAny

from services.embedding_service import get_embedding, EMBEDDING_DIM

qdrant_client: Optional[QdrantClient] = None
QDRANT_COLLECTION_COMMERCE = os.getenv("QDRANT_COLLECTION_COMMERCE", "commerce_knowledge")

COMMERCE_POLICY_DOC_TYPES: List[str] = [
    # 압축된 정책 RAG(doc_type)만 사용
    "goal_policy",
    "category_policy",
]

try:
    qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
    qdrant_client = QdrantClient(url=qdrant_url)
    print(f"Qdrant 연결 성공: {qdrant_url}")
except Exception as e:
    print(f"Qdrant 연결 실패 (RAG 없이 동작): {e}")
    qdrant_client = None

_collection_dim_checked = False
_collection_dim_error: Optional[str] = None


class CommerceRagResult(TypedDict, total=False):
    results: List[Dict[str, Any]]
    rag_hit: bool
    retrieved_chunks: int
    error: Optional[str]


def _build_policy_content_from_payload(payload: Dict[str, Any]) -> str:
    """
    정책 RAG 문서의 payload에서 LLM이 보기 좋은 content 문자열을 생성한다.

    - content 필드가 이미 있으면 그대로 사용
    - goal/category 정책 문서인 경우 rules/per_category를 사람이 읽을 수 있는 텍스트로 직렬화
      (setup_rag.py에서 rules/per_category를 payload에 포함시킨 뒤 사용하는 것을 전제로 함)
    """
    raw_content = (payload.get("content") or "").strip()
    if raw_content:
        return raw_content

    doc_type = payload.get("doc_type", "")
    if doc_type not in ("goal_policy", "category_policy"):
        return raw_content

    parts: List[str] = []
    rules = payload.get("rules") or {}
    per_category = payload.get("per_category") or {}

    if rules:
        parts.append("주요 규칙:")
        try:
            parts.append(json.dumps(rules, ensure_ascii=False))
        except Exception:
            parts.append(str(rules))

    if per_category:
        parts.append("카테고리별 규칙:")
        try:
            parts.append(json.dumps(per_category, ensure_ascii=False))
        except Exception:
            parts.append(str(per_category))

    return "\n".join(parts).strip()


def _build_intent_example_content_from_payload(payload: Dict[str, Any]) -> str:
    """
    intent_example 문서의 payload에서 few-shot 예시로 쓸 content 문자열을 생성한다.

    - content 필드가 이미 있으면 그대로 사용
    - 없으면 utterance + intent_result(JSON)를 합쳐서 생성
    """
    raw_content = (payload.get("content") or "").strip()
    if raw_content:
        return raw_content

    utterance = payload.get("utterance") or ""
    intent_result = payload.get("intent_result") or {}

    parts: List[str] = []
    if utterance:
        parts.append(f"사용자 발화: {utterance}")
    if intent_result:
        try:
            intent_json = json.dumps(intent_result, ensure_ascii=False)
        except Exception:
            intent_json = str(intent_result)
        parts.append(f"의도/슬롯 결과: {intent_json}")

    return "\n".join(parts).strip()


def search_commerce_rag(
    goal: Optional[str] = None,
    product_category: Optional[str] = None,
    doc_types: Optional[List[str]] = None,
    query_text: Optional[str] = None,
    limit: int = 10
) -> CommerceRagResult:
    empty_fail = lambda err: CommerceRagResult(
        results=[], rag_hit=False, retrieved_chunks=0, error=err
    )

    if not qdrant_client:
        print("[commerce_rag] rag_hit=false retrieved_chunks=0 error=qdrant_not_connected")
        return empty_fail("qdrant_not_connected")

    global _collection_dim_checked, _collection_dim_error
    if _collection_dim_checked and _collection_dim_error is not None:
        return empty_fail(_collection_dim_error)

    try:
        if not _collection_dim_checked:
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
                    _collection_dim_error = f"dimension_mismatch expected={EMBEDDING_DIM} got={size}"
                    _collection_dim_checked = True
                    print(f"[commerce_rag] {_collection_dim_error} -> rag_hit=false retrieved_chunks=0")
                    return empty_fail(_collection_dim_error)
                _collection_dim_checked = True
                _collection_dim_error = None
            except Exception as e:
                _collection_dim_error = f"collection_info_error: {e}"
                _collection_dim_checked = True
                print(f"[commerce_rag] {_collection_dim_error} -> rag_hit=false retrieved_chunks=0")
                return empty_fail(_collection_dim_error)

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
                payload = result.payload
                content = _build_policy_content_from_payload(payload)
                results.append({
                    "doc_type": payload.get("doc_type", ""),
                    "goal": payload.get("goal", ""),
                    "product_category": payload.get("product_category", ""),
                    "title": payload.get("title", ""),
                    "content": content,
                    "section": payload.get("section", ""),
                    "tags": payload.get("tags", []),
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


def search_commerce_intent_examples(
    query_text: str,
    limit: int = 3,
) -> CommerceRagResult:
    """
    intent/slot 추출용 few-shot 예시를 위한 RAG 검색.
    - doc_type="intent_example" & domain="commerce" 만 조회한다.
    """
    empty_fail = lambda err: CommerceRagResult(
        results=[], rag_hit=False, retrieved_chunks=0, error=err
    )

    if not qdrant_client:
        print("[commerce_rag:intent] rag_hit=false retrieved_chunks=0 error=qdrant_not_connected")
        return empty_fail("qdrant_not_connected")

    global _collection_dim_checked, _collection_dim_error
    if _collection_dim_checked and _collection_dim_error is not None:
        return empty_fail(_collection_dim_error)

    try:
        if not _collection_dim_checked:
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
                    _collection_dim_error = f"dimension_mismatch expected={EMBEDDING_DIM} got={size}"
                    _collection_dim_checked = True
                    print(f"[commerce_rag:intent] {_collection_dim_error} -> rag_hit=false retrieved_chunks=0")
                    return empty_fail(_collection_dim_error)
                _collection_dim_checked = True
                _collection_dim_error = None
            except Exception as e:
                _collection_dim_error = f"collection_info_error: {e}"
                _collection_dim_checked = True
                print(f"[commerce_rag:intent] {_collection_dim_error} -> rag_hit=false retrieved_chunks=0")
                return empty_fail(_collection_dim_error)

        # intent_example 전용 필터
        filter_conditions = [
            FieldCondition(key="domain", match=MatchValue(value="commerce")),
            FieldCondition(key="doc_type", match=MatchValue(value="intent_example")),
        ]
        query_filter = Filter(must=filter_conditions)

        query_vector = get_embedding(query_text)
        if not query_vector:
            print("[commerce_rag:intent] rag_hit=false retrieved_chunks=0 error=embedding_failed")
            return empty_fail("embedding_failed")

        search_results = qdrant_client.search(
            collection_name=QDRANT_COLLECTION_COMMERCE,
            query_vector=query_vector,
            query_filter=query_filter,
            limit=limit,
        )

        results = []
        for result in search_results:
            if result.payload:
                payload = result.payload
                content = _build_intent_example_content_from_payload(payload)
                results.append(
                    {
                        "doc_type": payload.get("doc_type", ""),
                        "goal": payload.get("goal", ""),
                        "product_category": payload.get("product_category", ""),
                        "title": payload.get("title", ""),
                        "content": content,
                        "section": payload.get("section", ""),
                        "tags": payload.get("tags", []),
                        "score": result.score,
                    }
                )

        n = len(results)
        if n == 0:
            print("[commerce_rag:intent] rag_hit=false retrieved_chunks=0 (no results)")
        else:
            print(f"[commerce_rag:intent] rag_hit=true retrieved_chunks={n}")
        return CommerceRagResult(
            results=results,
            rag_hit=(n > 0),
            retrieved_chunks=n,
        )
    except Exception as e:
        err_msg = str(e)
        print(f"[commerce_rag:intent] rag_hit=false retrieved_chunks=0 error=search_failed: {err_msg}")
        return empty_fail(f"search_failed: {err_msg}")
