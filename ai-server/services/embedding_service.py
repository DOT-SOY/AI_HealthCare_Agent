"""
임베딩 관련 서비스
Sentence-Transformers 기반 로컬 텍스트 임베딩 (384차원).
Qdrant 컬렉션은 반드시 이 차원으로 생성되어야 함 (setup_rag.py로 재생성).
"""
from typing import List, Optional

from sentence_transformers import SentenceTransformer


# Qdrant·RAG와 통일: paraphrase-multilingual-MiniLM-L12-v2 출력 차원
EMBEDDING_DIM = 384
MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"

# Sentence-Transformers 모델 싱글톤 (startup 시 1회 로딩 권장)
_model: Optional[SentenceTransformer] = None


def _get_model() -> SentenceTransformer:
    """
    Sentence-Transformers 모델을 로드하여 반환합니다.
    - 한국어 포함 멀티언어 지원 모델 사용
    """
    global _model
    if _model is None:
        print(f"[임베딩] SentenceTransformer 모델 로딩 중... ({MODEL_NAME})")
        _model = SentenceTransformer(MODEL_NAME)
        print(f"[임베딩] SentenceTransformer 모델 로딩 완료 (차원: {EMBEDDING_DIM})")
    return _model


def load_embedding_model() -> None:
    """
    앱 startup 시 1회 호출하여 임베딩 모델을 미리 로드합니다.
    요청 핸들러에서 첫 검색 시 로딩되는 8초 지연을 방지합니다.
    """
    _get_model()


def get_embedding(text: str) -> Optional[List[float]]:
    """
    텍스트를 Sentence-Transformers 임베딩 벡터로 변환합니다.

    반환:
        정규화된 임베딩 벡터 (리스트, 384차원) 또는 실패 시 None
    """
    try:
        model = _get_model()
        embedding = model.encode(text, normalize_embeddings=True)
        vec = embedding.tolist()
        if len(vec) != EMBEDDING_DIM:
            print(f"[임베딩] 차원 불일치: expected {EMBEDDING_DIM}, got {len(vec)}")
            return None
        return vec
    except Exception as e:
        print(f"임베딩 생성 실패: {e}")
        return None


