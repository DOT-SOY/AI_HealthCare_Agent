"""
Meal 전용 Gemini 임베딩 서비스
"""

import os
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from typing import List, Optional

from google import genai
from google.genai import types

_EXECUTOR = ThreadPoolExecutor(max_workers=2)


def _get_client() -> genai.Client:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is not set")
    return genai.Client(api_key=api_key)


def _embedding_model() -> str:
    return os.getenv("GEMINI_EMBEDDING_MODEL", "text-embedding-004")


def get_embedding(text: str) -> Optional[List[float]]:
    """
    Gemini 임베딩 생성 (Meal vector search 용)
    """
    if not text:
        return None
    client = _get_client()
    model_name = _embedding_model()

    def _call():
        return client.models.embed_content(
            model=model_name,
            contents=text,
            config=types.EmbedContentConfig(),
        )

    try:
        fut = _EXECUTOR.submit(_call)
        try:
            resp = fut.result(timeout=float(os.getenv("GEMINI_EMBED_TIMEOUT_SECONDS", "15")))
        except FutureTimeoutError:
            fut.cancel()
            return None
    except Exception as e:
        print(f"[meal_embedding_service] Gemini embedding failed: {e}")
        return None

    try:
        if hasattr(resp, "embeddings") and resp.embeddings:
            emb = resp.embeddings[0]
            if hasattr(emb, "values"):
                return list(emb.values)
            if isinstance(emb, dict) and "values" in emb:
                return list(emb["values"])
        if isinstance(resp, dict) and "embeddings" in resp and resp["embeddings"]:
            emb = resp["embeddings"][0]
            if isinstance(emb, dict) and "values" in emb:
                return list(emb["values"])
    except Exception as e:
        print(f"[meal_embedding_service] Gemini embedding parse failed: {e}")
        return None

    return None



