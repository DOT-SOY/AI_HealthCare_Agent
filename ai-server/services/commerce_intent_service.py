"""
Commerce 의도 분류 및 Slot 추출 서비스
"""
from typing import Dict, Any
from services.ai_service import call_ai_json
from prompts.commerce_intent import SYSTEM_PROMPT


def extract_commerce_intent_and_slots(text: str) -> Dict[str, Any]:
    """
    Commerce 의도 및 Slot 추출
    
    Args:
        text: 사용자 발화
    
    Returns:
        {
            "intent": "PRODUCT_RECOMMEND",
            "goal": "DIET|MAINTAIN|BULK_UP|ALL",
            "product_category": "FOOD|SUPPLEMENT|HEALTH_GOODS|CLOTHING|ETC|ALL",
            "budget": 숫자 또는 null,
            "avoid": ["키워드1", "키워드2", ...]
        }
    """
    try:
        result = call_ai_json(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=text,
            temperature=0.0  # 분류 작업이므로 최저 temperature로 일관성 극대화
        )
        
        # 기본값 설정
        return {
            "intent": result.get("intent", "PRODUCT_RECOMMEND"),
            "goal": result.get("goal", "ALL"),
            "product_category": result.get("product_category", "ALL"),
            "budget": result.get("budget"),
            "avoid": result.get("avoid", [])
        }
    except Exception as e:
        print(f"Commerce 의도 분류 실패: {e}")
        # 기본값 반환
        return {
            "intent": "PRODUCT_RECOMMEND",
            "goal": "ALL",
            "product_category": "ALL",
            "budget": None,
            "avoid": []
        }

