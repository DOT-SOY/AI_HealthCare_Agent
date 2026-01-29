"""
FastAPI 애플리케이션 진입점
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import meal

app = FastAPI(
    title="AI Meal Service",
    description="Gemini 3 Pro 기반 식단 관리 AI 서버",
    version="1.0.0"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
app.include_router(meal.router, prefix="/api/meal", tags=["meal"])


@app.get("/")
async def root():
    """헬스 체크"""
    return {"status": "ok", "service": "AI Meal Service"}


@app.get("/health")
async def health():
    """상세 헬스 체크"""
    return {
        "status": "healthy",
        "service": "AI Meal Service",
        "version": "1.0.0"
    }

