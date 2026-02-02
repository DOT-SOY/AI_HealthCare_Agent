"""
임베딩 관련 서비스
Sentence-Transformers 기반 로컬 텍스트 임베딩
"""
from typing import List, Optional

from sentence_transformers import SentenceTransformer


# Sentence-Transformers 모델 싱글톤
_model: Optional[SentenceTransformer] = None


def _get_model() -> SentenceTransformer:
    """
    Sentence-Transformers 모델을 로드하여 반환합니다.
    - 한국어 포함 멀티언어 지원 모델 사용
    """
    global _model
    if _model is None:
        # 멀티언어 문장 임베딩에 많이 사용하는 경량 모델
        model_name = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
        print(f"[임베딩] SentenceTransformer 모델 로딩 중... ({model_name})")
        _model = SentenceTransformer(model_name)
        print("[임베딩] SentenceTransformer 모델 로딩 완료")
    return _model


def get_embedding(text: str) -> Optional[List[float]]:
    """
    텍스트를 Sentence-Transformers 임베딩 벡터로 변환합니다.

    반환:
        정규화된 임베딩 벡터 (리스트) 또는 실패 시 None
    """
    try:
        model = _get_model()
        # normalize_embeddings=True 로 코사인 유사도 계산에 바로 사용 가능
        embedding = model.encode(text, normalize_embeddings=True)
        return embedding.tolist()
    except Exception as e:
        print(f"임베딩 생성 실패: {e}")
        return None


