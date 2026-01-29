"""
RAG 데이터 설정 스크립트
프로젝트 루트에서 실행: python setup_rag.py
"""
import sys
import os

# python/app 경로를 Python path에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'app'))

from app.utils.rag_setup import setup_rag_data
import asyncio

if __name__ == "__main__":
    asyncio.run(setup_rag_data())

