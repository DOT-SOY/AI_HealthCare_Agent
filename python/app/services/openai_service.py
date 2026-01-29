"""
OpenAI GPT 서비스
gpt-4o-mini를 사용한 LLM 서비스
"""
from openai import AsyncOpenAI
from app.config import settings
from app.models.request import MealDto, GoalSpec, UserProfile
from app.services.rag_service import RAGService
import json
import logging

logger = logging.getLogger(__name__)


class OpenAIService:
    """OpenAI GPT 서비스"""
    
    def __init__(self):
        self.client = AsyncOpenAI(api_key=settings.openai_api_key)
        self.model = settings.openai_model
        self.rag_service = RAGService()
    
    async def generate_meals(
        self,
        profile: UserProfile,
        goal: GoalSpec
    ) -> list[MealDto]:
        """
        초기 식단 생성 (LLM + RAG)
        """
        prompt = f"""
        사용자 프로필:
        - 나이: {profile.age}
        - 성별: {profile.gender}
        - 키: {profile.height}cm
        - 몸무게: {profile.weight}kg
        - 활동 수준: {profile.activityLevel}
        - 알레르기: {profile.allergies or []}
        - 좋아하는 음식: {profile.likedFoods or []}
        - 싫어하는 음식: {profile.dislikedFoods or []}
        
        목표:
        - 목표 타입: {goal.goalType}
        - 목표 칼로리: {goal.targetCalories}kcal
        - 목표 탄수화물: {goal.targetCarbs}g
        - 목표 단백질: {goal.targetProtein}g
        - 목표 지방: {goal.targetFat}g
        - 하루 식사 횟수: {goal.mealCount or 3}끼
        
        위 정보를 바탕으로 하루 식단을 생성해주세요.
        각 음식의 정확한 영양성분은 RAG 검색을 통해 확인하세요.
        
        응답 형식 (JSON):
        {{
            "meals": [
                {{
                    "mealTime": "BREAKFAST",
                    "foodName": "음식명",
                    "servingSize": "1인분",
                    "calories": 칼로리,
                    "carbs": 탄수화물,
                    "protein": 단백질,
                    "fat": 지방
                }},
                ...
            ]
        }}
        """
        
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "You are a nutrition expert. Always respond in valid JSON format."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7
            )
            
            result = json.loads(response.choices[0].message.content)
            
            meals = []
            for meal_data in result.get("meals", []):
                # RAG로 영양성분 검증 및 보완
                nutrition = await self.rag_service.search_nutrition(meal_data["foodName"])
                if nutrition:
                    meal_data.update(nutrition)
                
                meals.append(MealDto(
                    mealTime=meal_data.get("mealTime"),
                    foodName=meal_data.get("foodName"),
                    servingSize=meal_data.get("servingSize", "1인분"),
                    calories=meal_data.get("calories"),
                    carbs=meal_data.get("carbs"),
                    protein=meal_data.get("protein"),
                    fat=meal_data.get("fat"),
                    status="PLANNED",
                    isAdditional=False
                ))
            
            return meals
        except Exception as e:
            logger.error(f"식단 생성 실패: {e}", exc_info=True)
            raise
    
    async def replan_meals(
        self,
        goal: GoalSpec,
        current_meals: list[MealDto]
    ) -> list[MealDto]:
        """
        식단 재분배 (LLM + RAG)
        잔여 영양소 목표에 맞춰 식단 재구성
        """
        # 현재까지 섭취한 영양소 계산
        consumed = {
            "calories": sum(m.calories or 0 for m in current_meals if m.status == "EATEN"),
            "carbs": sum(m.carbs or 0 for m in current_meals if m.status == "EATEN"),
            "protein": sum(m.protein or 0 for m in current_meals if m.status == "EATEN"),
            "fat": sum(m.fat or 0 for m in current_meals if m.status == "EATEN")
        }
        
        # 잔여 목표 계산
        remaining = {
            "calories": (goal.targetCalories or 0) - consumed["calories"],
            "carbs": (goal.targetCarbs or 0) - consumed["carbs"],
            "protein": (goal.targetProtein or 0) - consumed["protein"],
            "fat": (goal.targetFat or 0) - consumed["fat"]
        }
        
        prompt = f"""
        현재까지 섭취한 영양소:
        - 칼로리: {consumed['calories']}kcal
        - 탄수화물: {consumed['carbs']}g
        - 단백질: {consumed['protein']}g
        - 지방: {consumed['fat']}g
        
        잔여 목표 영양소:
        - 칼로리: {remaining['calories']}kcal
        - 탄수화물: {remaining['carbs']}g
        - 단백질: {remaining['protein']}g
        - 지방: {remaining['fat']}g
        
        위 잔여 목표에 맞춰 남은 식사(PLANNED 상태)를 재구성해주세요.
        각 음식의 정확한 영양성분은 RAG 검색을 통해 확인하세요.
        
        응답 형식 (JSON):
        {{
            "meals": [
                {{
                    "mealTime": "LUNCH",
                    "foodName": "음식명",
                    "servingSize": "1인분",
                    "calories": 칼로리,
                    "carbs": 탄수화물,
                    "protein": 단백질,
                    "fat": 지방
                }},
                ...
            ]
        }}
        """
        
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "You are a nutrition expert. Always respond in valid JSON format."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7
            )
            
            result = json.loads(response.choices[0].message.content)
            
            meals = []
            for meal_data in result.get("meals", []):
                # RAG로 영양성분 검증 및 보완
                nutrition = await self.rag_service.search_nutrition(meal_data["foodName"])
                if nutrition:
                    meal_data.update(nutrition)
                
                meals.append(MealDto(
                    mealTime=meal_data.get("mealTime"),
                    foodName=meal_data.get("foodName"),
                    servingSize=meal_data.get("servingSize", "1인분"),
                    calories=meal_data.get("calories"),
                    carbs=meal_data.get("carbs"),
                    protein=meal_data.get("protein"),
                    fat=meal_data.get("fat"),
                    status="PLANNED",
                    isAdditional=False
                ))
            
            return meals
        except Exception as e:
            logger.error(f"식단 재구성 실패: {e}", exc_info=True)
            raise
    
    async def generate_advice(self, current_meals: list[MealDto]) -> str:
        """
        심층 영양 상담 (LLM + RAG)
        일반론으로만 제한 (WHO/CDC/식약처 공통 내용)
        """
        # 하루 총 영양소 계산
        total = {
            "calories": sum(m.calories or 0 for m in current_meals),
            "carbs": sum(m.carbs or 0 for m in current_meals),
            "protein": sum(m.protein or 0 for m in current_meals),
            "fat": sum(m.fat or 0 for m in current_meals),
            "sodium": 0  # 나트륨은 RAG에서 가져올 수 있으면 추가
        }
        
        meals_summary = "\n".join([
            f"- {m.mealTime}: {m.foodName} ({m.calories}kcal)"
            for m in current_meals
        ])
        
        prompt = f"""
        오늘 섭취한 식단:
        {meals_summary}
        
        총 영양소:
        - 칼로리: {total['calories']}kcal
        - 탄수화물: {total['carbs']}g
        - 단백질: {total['protein']}g
        - 지방: {total['fat']}g
        
        위 식단을 분석하여 **일반론적인 영양 상담**을 제공해주세요.
        
        중요 제약사항:
        1. **반드시 일반론으로만 제한** (WHO, CDC, 식약처가 공통으로 말하는 내용만)
        2. 개인 맞춤형 진단이나 특정 질병 언급 금지
        3. "일반적으로 연구에 따르면..." 형식으로 시작
        4. 나트륨, 당분, 포화지방 등 과다 섭취 시 건강 위험에 대한 일반적인 정보 제공
        5. 균형잡힌 식단의 중요성에 대한 일반론적 조언
        
        예시:
        "일반적으로 연구에 따르면 나트륨 과다 섭취는 고혈압 위험을 높이는 것으로 알려져 있습니다."
        
        응답은 한국어로 3-5문단으로 작성해주세요.
        """
        
        try:
            response = await self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "You are a nutrition expert providing general dietary advice based on WHO, CDC, and Korean FDA guidelines."},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.7
            )
            
            return response.choices[0].message.content
        except Exception as e:
            logger.error(f"영양 상담 생성 실패: {e}", exc_info=True)
            raise

