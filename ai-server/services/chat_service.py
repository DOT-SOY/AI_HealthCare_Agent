"""
채팅 응답 서비스
"""
from typing import Dict, Any
from services.ai_service import call_ai
from prompts.chat_response import PROMPTS


def generate_ai_answer(text: str, intent: str, entities: Dict[str, Any] = None) -> str:
    """의도에 따른 AI 답변 생성"""
    system_prompt = PROMPTS.get(intent, PROMPTS["GENERAL_CHAT"])
    
    try:
        return call_ai(
            system_prompt=system_prompt,
            user_prompt=text,
            temperature=0.7
        )
    except Exception as e:
        print(f"AI 답변 생성 실패: {e}")
        return "죄송합니다. 답변을 생성하는 중 오류가 발생했습니다."


