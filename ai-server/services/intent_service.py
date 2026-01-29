"""
의도 분류 서비스
"""
from typing import Dict, Any
from services.ai_service import call_ai_json
from prompts.intent_classification import SYSTEM_PROMPT


def classify_intent(text: str) -> Dict[str, Any]:
    """LLM 기반 의도 분류"""
    try:
        result = call_ai_json(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=text,
            temperature=0.3
        )
        return result
    except Exception as e:
        print(f"의도 분류 실패: {e}")
        # 기본값 반환
        return {
            "intent": "GENERAL_CHAT",
            "entities": {}
        }


