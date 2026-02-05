"""
AI 호출 관련 서비스 (공통)

원칙:
- 식단(Meal) 기능은 Gemini를 사용합니다.
- 그 외 일반 기능(의도 분류, 통증 상담 등)은 OpenAI(GPT)를 사용합니다.
"""

import os
import json
from typing import Dict, Any, Optional

# Gemini 서비스 (식단용)
from services.gemini_service import generate_text as gemini_generate_text
from services.gemini_service import generate_json as gemini_generate_json
from services.gemini_service import generate_vision_json as gemini_generate_vision_json

# OpenAI 클라이언트 (일반 기능용)
from openai import OpenAI

try:
    openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
except Exception as e:
    print(f"OpenAI Client Init Fail: {e}")
    openai_client = None

OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
# 통증 조언용 모델 (간단한 대답용)
PAIN_ADVICE_MODEL = os.getenv("PAIN_ADVICE_MODEL", "gpt-4o-mini")


def call_ai(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7,
    response_format: Optional[Dict[str, str]] = None,
    model: Optional[str] = None
) -> str:
    """
    공통 AI 호출 함수 (OpenAI 사용)
    - 식단 외 일반 채팅/통증 상담 등에 사용
    """
    if not openai_client:
        return "OpenAI API 키가 설정되지 않았습니다."

    used_model = model if model else OPENAI_MODEL

    # response_format 처리
    kwargs: Dict[str, Any] = {}
    if response_format:
        kwargs["response_format"] = response_format

    try:
        response = openai_client.chat.completions.create(
            model=used_model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=temperature,
            **kwargs,
        )
        return response.choices[0].message.content
    except Exception as e:
        print(f"OpenAI Call Error: {e}")
        return f"AI 호출 중 오류 발생: {e}"


def call_ai_json(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7
) -> Dict[str, Any]:
    """
    JSON 형식으로 응답받는 AI 호출 (OpenAI 사용)
    - 의도 분류 등에 사용
    """
    if not openai_client:
        return {"error": "OpenAI API Key missing"}

    try:
        response = openai_client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=temperature,
            response_format={"type": "json_object"},
        )
        content = response.choices[0].message.content
        return json.loads(content)
    except Exception as e:
        print(f"OpenAI JSON Call Error: {e}")
        return {"error": str(e), "intent": "GENERAL_CHAT", "entities": {}}


# --- 식단 전용 Gemini 래퍼 함수들 ---

def call_meal_ai_text(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7,
    model: Optional[str] = None,
    timeout_seconds: float = 25.0,
) -> str:
    """
    식단(Meal) 기능을 위한 Gemini 텍스트 호출
    """
    return gemini_generate_text(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        temperature=temperature,
        model=model,
        timeout_seconds=timeout_seconds,
    )


def call_meal_ai_json(
    system_prompt: str,
    user_prompt: str,
    temperature: float = 0.7,
    model: Optional[str] = None,
    timeout_seconds: float = 25.0,
) -> Dict[str, Any]:
    """
    식단(Meal) 기능을 위한 Gemini JSON 호출
    """
    return gemini_generate_json(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        model=model,
        temperature=temperature,
        timeout_seconds=timeout_seconds,
    )


def call_ai_vision_json(
    system_prompt: str,
    user_prompt: str,
    image_base64: str,
    temperature: float = 0.3,
) -> Dict[str, Any]:
    """
    이미지 분석(식단)은 Gemini Vision 사용
    """
    return gemini_generate_vision_json(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        image_base64=image_base64,
        temperature=temperature,
    )


