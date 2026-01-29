"""
Java AiMealRequestDto와 대응하는 Python Pydantic 모델
"""
from typing import Optional, List
from pydantic import BaseModel, Field


class UserProfile(BaseModel):
    """사용자 프로필"""
    userId: Optional[int] = None
    age: Optional[int] = None
    gender: Optional[str] = None
    height: Optional[float] = None
    weight: Optional[float] = None
    activityLevel: Optional[str] = None
    allergies: Optional[List[str]] = None
    likedFoods: Optional[List[str]] = None
    dislikedFoods: Optional[List[str]] = None


class GoalSpec(BaseModel):
    """목표 상세"""
    goalType: Optional[str] = None  # DIET, BULK_UP, MAINTAIN
    targetCalories: Optional[int] = None
    targetCarbs: Optional[int] = None
    targetProtein: Optional[int] = None
    targetFat: Optional[int] = None
    mealCount: Optional[int] = None


class MealDto(BaseModel):
    """식단 DTO"""
    scheduleId: Optional[int] = None
    userId: Optional[int] = None
    mealDate: Optional[str] = None
    mealTime: Optional[str] = None  # BREAKFAST, LUNCH, DINNER, SNACK
    status: Optional[str] = None  # EATEN, PLANNED, SKIPPED
    isAdditional: Optional[bool] = None
    foodName: Optional[str] = None
    servingSize: Optional[str] = None
    calories: Optional[int] = None
    carbs: Optional[int] = None
    protein: Optional[int] = None
    fat: Optional[int] = None
    originalFoodName: Optional[str] = None
    originalServingSize: Optional[str] = None
    originalCalories: Optional[int] = None
    originalCarbs: Optional[int] = None
    originalProtein: Optional[int] = None
    originalFat: Optional[int] = None


class AiMealRequest(BaseModel):
    """AI 요청 DTO (Java AiMealRequestDto 대응)"""
    requestType: str = Field(..., description="요청 타입: GENERATE, REPLAN, ANALYZE_IMAGE, ADVICE")
    profile: Optional[UserProfile] = None
    goal: Optional[GoalSpec] = None
    currentMeals: Optional[List[MealDto]] = None
    userQuestion: Optional[str] = None
    foodImageBase64: Optional[str] = Field(None, description="Base64 인코딩된 음식 이미지")

