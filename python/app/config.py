"""
환경변수 설정 관리
"""
import os
from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """애플리케이션 설정"""
    
    # OpenAI API
    openai_api_key: str
    openai_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    
    # Qdrant
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "food_nutrition"
    
    # Qdrant (하위 호환성)
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    qdrant_collection_name: str = "food_nutrition"
    
    # 식약처 API (선택)
    mfds_api_key: Optional[str] = None
    
    # 서버 설정
    server_port: int = 8000
    server_host: str = "0.0.0.0"
    
    # CORS
    allowed_origins: list[str] = ["http://localhost:8080", "http://localhost:5173"]
    
    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()

