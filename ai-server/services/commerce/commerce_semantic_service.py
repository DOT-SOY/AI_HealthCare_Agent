"""Commerce semantic scoring 서비스.

현재는 Backend에서 keyword 기반 점수 계산을 수행하므로,
이 모듈은 향후 벡터 임베딩 인프라 도입 시 사용할 설정/유틸리티만 제공.
"""
import os
from typing import Dict, Any

# 임베딩 설정 (환경변수 또는 설정 파일로 분리 가능)
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-ada-002")
EMBEDDING_DIMENSION = int(os.getenv("EMBEDDING_DIMENSION", "1536"))
PRODUCT_EMBEDDING_CACHE_KEY_PREFIX = "product:embedding:"
QUERY_EMBEDDING_CACHE_TTL_SEC = 1800  # 30분

# 설정/상수
SEMANTIC_CONFIG = {
    "embedding_model": EMBEDDING_MODEL,
    "embedding_dimension": EMBEDDING_DIMENSION,
    "product_cache_key_prefix": PRODUCT_EMBEDDING_CACHE_KEY_PREFIX,
    "query_cache_ttl_sec": QUERY_EMBEDDING_CACHE_TTL_SEC,
    # 현재는 Backend에서 keyword scoring 수행
    "use_backend_keyword_scoring": True,
}


def get_semantic_config() -> Dict[str, Any]:
    """현재 semantic scoring 설정 반환."""
    return dict(SEMANTIC_CONFIG)
