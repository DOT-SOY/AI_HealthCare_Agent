"""
AI 호출 관련 서비스 (공통)
"""
import os
import json
from typing import Dict, Any, Optional
from openai import OpenAI

# OpenAI 클라이언트 초기화
openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
# 통증 조언용 모델 (간단한 대답용)
PAIN_ADVICE_MODEL = os.getenv("PAIN_ADVICE_MODEL", "gpt-4.1-nano")


def call_ai(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7,
    response_format: Optional[Dict[str, str]] = None,
    model: Optional[str] = None
) -> str:
    """
    공통 AI 호출 함수
    
    Args:
        system_prompt: 시스템 프롬프트
        user_prompt: 사용자 프롬프트
        temperature: 온도 (기본값 0.7)
        response_format: 응답 형식 (JSON 등)
        model: 사용할 모델 (None이면 기본 모델 사용)
    
    Returns:
        AI 응답 텍스트
    """
    try:
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ]
        
        params = {
            "model": model or OPENAI_MODEL,
            "messages": messages,
            "temperature": temperature
        }
        
        if response_format:
            params["response_format"] = response_format
        
        response = openai_client.chat.completions.create(**params)
        return response.choices[0].message.content
    except Exception as e:
        print(f"AI 호출 실패: {e}")
        raise


def call_ai_json(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7
) -> Dict[str, Any]:
    """
    JSON 형식으로 응답받는 AI 호출
    
    Returns:
        파싱된 JSON 딕셔너리
    """
    response = call_ai(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        temperature=temperature,
        response_format={"type": "json_object"}
    )
    return json.loads(response)



