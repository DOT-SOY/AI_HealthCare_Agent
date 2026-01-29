"""
Java AiMealResponseDto와 대응하는 Python Pydantic 모델
"""
from typing import Optional, List
from pydantic import BaseModel
from .request import MealDto


class AnalyzedFood(BaseModel):
    """Vision 분석 결과"""
    foodName: str
    calories: Optional[int] = None
    carbs: Optional[int] = None
    protein: Optional[int] = None
    fat: Optional[int] = None
    confidence: Optional[float] = None  # AI 확신도 (0.0 ~ 1.0)


class AiMealResponse(BaseModel):
    """AI 응답 DTO (Java AiMealResponseDto 대응)"""
    suggestedMeals: Optional[List[MealDto]] = None  # 식단 생성/재분배 결과
    analyzedFood: Optional[AnalyzedFood] = None  # Vision 분석 결과
    adviceComment: Optional[str] = None  # 심층 상담 결과

