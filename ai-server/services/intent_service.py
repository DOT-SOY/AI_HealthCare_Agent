"""
의도 분류 서비스
"""
from typing import Dict, Any
from datetime import datetime
from services.ai_service import call_ai_json
from prompts.intent_classification import SYSTEM_PROMPT


def classify_intent(text: str) -> Dict[str, Any]:
    """LLM 기반 의도 분류"""
    try:
        # 현재 날짜를 YYYY-MM-DD 형식으로 가져와서 SYSTEM_PROMPT의 {current_date}를 치환
        current_date = datetime.now().strftime("%Y-%m-%d")
        system_prompt = SYSTEM_PROMPT.format(current_date=current_date)
        
        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=text,
            temperature=0.0  # 분류 작업이므로 최저 temperature로 일관성 극대화
        )
        return result
    except Exception as e:
        print(f"의도 분류 실패: {e}")
        # 기본값 반환
        return {
            "intent": "GENERAL_CHAT",
            "entities": {}
        }


