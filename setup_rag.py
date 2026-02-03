"""
RAG 데이터를 Qdrant에 업로드하는 단일 스크립트 (최종 병합본)

- 임베딩: SentenceTransformer(라이브러리)만 사용 → 384차원 (paraphrase-multilingual-MiniLM-L12-v2)
- ai-server의 embedding_service.EMBEDDING_DIM(384) 및 Qdrant 컬렉션 차원과 반드시 일치해야 함.
  - 기존 컬렉션이 1536차원(OpenAI 등)이면 RAG 검색이 실패함. RECREATE_COLLECTION=true로 이 스크립트를
    재실행하여 384차원 컬렉션으로 재생성 및 전량 재임베딩 필요.
- 컬렉션: domain 기준으로 2개로 분리 업로드
  - domain == "commerce"  -> QDRANT_COLLECTION_COMMERCE (기본: commerce_knowledge)
  - else                 -> QDRANT_COLLECTION_EXERCISE (기본: exercise_knowledge)

- 옵션 환경변수:
  - QDRANT_URL: 기본 http://localhost:6333
  - QDRANT_COLLECTION_COMMERCE: 기본 commerce_knowledge
  - QDRANT_COLLECTION_EXERCISE: 기본 exercise_knowledge
  - ST_MODEL_NAME: 기본 sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
  - RAG_DATA_PATH: 기본 rag_data.json
  - RECREATE_COLLECTION: true/false (기본 true)  # true면 컬렉션 삭제 후 재생성
"""

import json
import os
import pathlib
import uuid

from typing import Any, Dict, List, Optional, Tuple

from dotenv import load_dotenv
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct

# -----------------------------
# .env 로드
# -----------------------------
# 프로젝트 구조에 맞춰 조정: 현재 파일 기준 ai-server/.env
env_path = pathlib.Path(__file__).parent / "ai-server" / ".env"
load_dotenv(dotenv_path=env_path)

# -----------------------------
# 설정
# -----------------------------
RAG_DATA_PATH = os.getenv("RAG_DATA_PATH", "rag_data.json")
RECREATE_COLLECTION = os.getenv("RECREATE_COLLECTION", "true").lower() == "true"

qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
qdrant_client = QdrantClient(url=qdrant_url)

QDRANT_COLLECTION_COMMERCE = os.getenv("QDRANT_COLLECTION_COMMERCE", "commerce_knowledge")
QDRANT_COLLECTION_EXERCISE = os.getenv("QDRANT_COLLECTION_EXERCISE", "exercise_knowledge")

ST_MODEL_NAME = os.getenv(
    "ST_MODEL_NAME",
    "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
)
# ai-server embedding_service.EMBEDDING_DIM과 동일해야 함 (384)
EMBEDDING_DIM_EXPECTED = 384

# lazy-load singleton
_st_model = None


def _get_st_model():
    global _st_model
    if _st_model is None:
        from sentence_transformers import SentenceTransformer
        print(f"[RAG] SentenceTransformer 모델 로딩 중... ({ST_MODEL_NAME})")
        _st_model = SentenceTransformer(ST_MODEL_NAME)
        print("[RAG] SentenceTransformer 모델 로딩 완료")
    return _st_model


def load_knowledge() -> List[Dict[str, Any]]:
    """RAG 데이터 로드"""
    try:
        with open(RAG_DATA_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            if not isinstance(data, list):
                raise ValueError("rag_data.json 최상위는 list여야 합니다.")
            return data
    except FileNotFoundError:
        print(f"[RAG] {RAG_DATA_PATH} 파일을 찾을 수 없습니다.")
        return []
    except json.JSONDecodeError as e:
        print(f"[RAG] JSON 파싱 오류: {e}")
        return []
    except Exception as e:
        print(f"[RAG] 로드 실패: {e}")
        return []


def split_by_domain(items: List[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """domain으로 commerce / exercise 분리 (없으면 exercise로)"""
    commerce: List[Dict[str, Any]] = []
    exercise: List[Dict[str, Any]] = []

    for it in items:
        if it.get("domain") == "commerce":
            commerce.append(it)
        else:
            exercise.append(it)

    return commerce, exercise


def make_payload(item: Dict[str, Any]) -> Dict[str, Any]:
    """payload 통합(두 스크립트 필드 합집합)"""
    payload: Dict[str, Any] = {
        "category": item.get("category", ""),
        "title": item.get("title", ""),
        "content": item.get("content", ""),
        "body_part": item.get("body_part", ""),
        "exercise_name": item.get("exercise_name", ""),
        "tags": item.get("tags", []),
    }

    # 확장 필드 (있으면 싣기)
    for k in [
        "doc_id",
        "chunk_id",
        "doc_type",
        "goal",
        "product_category",
        "domain",
        "version",
        "section",
    ]:
        if k in item:
            payload[k] = item.get(k)

    return payload


def make_point_id(item: Dict[str, Any], fallback_idx: int):
    """
    Qdrant Point ID 규칙: unsigned int 또는 UUID만 허용.

    정책(결정적):
    - doc_id(UUID) + chunk_id가 있으면: uuid5(doc_id, chunk_id)로 "항상 동일한 UUID" 생성
    - item["id"]가 UUID면 그대로 사용
    - item["id"]가 정수면 int로 사용
    - 그 외에는 fallback_idx 정수 사용
    """
    doc_id = item.get("doc_id")
    chunk_id = item.get("chunk_id")

    # 1) doc_id + chunk_id => 안정적인 UUID 생성
    if doc_id is not None and chunk_id is not None:
        try:
            ns = uuid.UUID(str(doc_id))  # doc_id가 UUID여야 함
            return str(uuid.uuid5(ns, str(chunk_id)))
        except Exception:
            # doc_id가 UUID가 아니면 안전하게 fallback
            return int(fallback_idx)

    # 2) item["id"] 사용 (UUID or int)
    raw_id = item.get("id")
    if raw_id is not None:
        # UUID면 그대로
        try:
            return str(uuid.UUID(str(raw_id)))
        except Exception:
            pass

        # 정수면 정수로
        try:
            return int(raw_id)
        except Exception:
            return int(fallback_idx)

    # 3) fallback: 정수
    return int(fallback_idx)



def get_embedding(text: str) -> Optional[List[float]]:
    """Sentence-Transformers 임베딩 생성"""
    try:
        model = _get_st_model()
        emb = model.encode(text, normalize_embeddings=True)
        return emb.tolist()
    except Exception as e:
        print(f"[RAG] 임베딩 생성 실패: {e}")
        return None


def ensure_collection(client: QdrantClient, collection_name: str, vector_size: int):
    """
    컬렉션 생성/재생성
    - RECREATE_COLLECTION=true이면 delete 후 create
    - false이면 없을 때만 create (스키마 mismatch는 사용자가 책임)
    """
    if RECREATE_COLLECTION:
        try:
            try:
                client.delete_collection(collection_name)
                print(f"✓ 기존 컬렉션 '{collection_name}' 삭제 완료")
            except Exception as e:
                if "doesn't exist" in str(e) or "not found" in str(e).lower():
                    print(f"  컬렉션 '{collection_name}'가 없습니다 (새로 생성합니다)")
                else:
                    print(f"  컬렉션 삭제 중 오류 (무시하고 계속): {e}")

            client.create_collection(
                collection_name=collection_name,
                vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE),
            )
            print(f"✓ 새 컬렉션 '{collection_name}' 생성 완료 (차원: {vector_size})")
        except Exception as e:
            print(f"✗ 컬렉션 생성 실패: {e}")
            raise
    else:
        try:
            client.get_collection(collection_name)
            print(f"✓ 컬렉션 '{collection_name}' 존재 확인 (재생성 안함)")
        except Exception:
            client.create_collection(
                collection_name=collection_name,
                vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE),
            )
            print(f"✓ 컬렉션 '{collection_name}' 생성 완료 (차원: {vector_size})")


def build_points(items: List[Dict[str, Any]]) -> Tuple[List[PointStruct], int]:
    """아이템 리스트를 PointStruct로 변환"""
    points: List[PointStruct] = []
    vector_size = 0

    for idx, item in enumerate(items, start=1):
        text = f"{item.get('title', '')} {item.get('content', '')}".strip()

        emb = get_embedding(text)
        if not emb:
            print(f"항목 {idx} 임베딩 실패, 건너뜀")
            continue

        if vector_size == 0:
            vector_size = len(emb)
            if vector_size != EMBEDDING_DIM_EXPECTED:
                print(f"[RAG] 경고: 임베딩 차원이 기대값과 다릅니다. expected={EMBEDDING_DIM_EXPECTED}, got={vector_size}")

        point_id = make_point_id(item, fallback_idx=idx)
        payload = make_payload(item)

        points.append(
            PointStruct(
                id=point_id,
                vector=emb,
                payload=payload,
            )
        )

        print(f"항목 {idx}/{len(items)} 처리 완료: {item.get('title', '')}")

    return points, vector_size


def upsert_points(collection_name: str, points: List[PointStruct]):
    if not points:
        print(f"[{collection_name}] 업로드할 항목이 없습니다.")
        return

    qdrant_client.upsert(collection_name=collection_name, points=points)
    print(f"[{collection_name}] {len(points)}개 업로드 완료")


def main():
    print("=" * 60)
    print("RAG 데이터 업로드 시작 (SentenceTransformer 전용)")
    print(f"- RAG_DATA_PATH: {RAG_DATA_PATH}")
    print(f"- QDRANT_URL: {qdrant_url}")
    print(f"- RECREATE_COLLECTION: {RECREATE_COLLECTION}")
    print(f"- ST_MODEL_NAME: {ST_MODEL_NAME}")
    print("=" * 60)

    data = load_knowledge()
    if not data:
        print("로드할 데이터가 없습니다.")
        return

    commerce_items, exercise_items = split_by_domain(data)

    # Commerce
    if commerce_items:
        print("\n[Commerce] 포인트 생성")
        commerce_points, commerce_vector_size = build_points(commerce_items)
        if commerce_points:
            ensure_collection(qdrant_client, QDRANT_COLLECTION_COMMERCE, commerce_vector_size)
            upsert_points(QDRANT_COLLECTION_COMMERCE, commerce_points)

    # Exercise
    if exercise_items:
        print("\n[Exercise] 포인트 생성")
        exercise_points, exercise_vector_size = build_points(exercise_items)
        if exercise_points:
            ensure_collection(qdrant_client, QDRANT_COLLECTION_EXERCISE, exercise_vector_size)
            upsert_points(QDRANT_COLLECTION_EXERCISE, exercise_points)

    print("\n" + "=" * 60)
    print("RAG 데이터 업로드 완료")
    print("=" * 60)


if __name__ == "__main__":
    main()
