"""
임베딩 관련 서비스
"""
import os
from typing import List, Optional
from openai import OpenAI

# OpenAI 클라이언트 초기화
openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")


def get_embedding(text: str) -> Optional[List[float]]:
    """OpenAI 임베딩 생성"""
    try:
        response = openai_client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=text
        )
        return response.data[0].embedding
    except Exception as e:
        print(f"임베딩 생성 실패: {e}")
        return None


