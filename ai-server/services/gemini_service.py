"""
Gemini 호출 서비스 (Google AI Studio API Key 사용)

역할:
- 텍스트 응답: generate_text
- JSON 응답(엄격): generate_json
- Vision + JSON 응답: generate_vision_json

주의:
- 프롬프트(의도/식단 등)는 prompts/*에서 관리하고, 여기서는 "호출/파싱/타임아웃"만 담당합니다.
"""

from __future__ import annotations

import base64
import json
import os
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from typing import Any, Dict, Optional

from google.genai import Client, types

_EXECUTOR = ThreadPoolExecutor(max_workers=4)


def _get_client() -> Client:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is not set")
    return Client(api_key=api_key)


def _default_model() -> str:
    # 통합 기본 모델 (meal 포함 공용)
    return os.getenv("MEAL_GEMINI_MODEL", "gemini-2.5-pro")


def _parse_json_loose(text: str) -> Dict[str, Any]:
    """
    모델이 JSON만 반환하도록 유도하지만, 가끔 코드블록/부가 텍스트가 섞일 수 있어 방어합니다.
    """
    if not text:
        return {}
    try:
        return json.loads(text)
    except Exception:
        pass

    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start : end + 1])
        except Exception:
            return {}
    return {}


def generate_text(
    *,
    system_prompt: str,
    user_prompt: str,
    model: Optional[str] = None,
    temperature: float = 0.7,
    timeout_seconds: float = 25.0,
) -> str:
    client = _get_client()
    model_name = model or _default_model()

    def _call() -> Any:
        # genai 라이브러리의 pydantic 직렬화 오류를 피하기 위해 Content 대신 Part 목록을 전달
        return client.models.generate_content(
            model=model_name,
            contents=[types.Part.from_text(text=f"{system_prompt}\n\n{user_prompt}")],
            config=types.GenerateContentConfig(
                temperature=temperature,
            ),
        )

    try:
        fut = _EXECUTOR.submit(_call)
        try:
            resp = fut.result(timeout=timeout_seconds)
        except FutureTimeoutError:
            fut.cancel()
            return ""
    except Exception as e:
        print(f"[gemini_service] generate_text failed: {e}")
        return ""

    return (getattr(resp, "text", "") or "").strip()


def generate_json(
    *,
    system_prompt: str,
    user_prompt: str,
    model: Optional[str] = None,
    temperature: float = 0.2,
    timeout_seconds: float = 25.0,
) -> Dict[str, Any]:
    client = _get_client()
    model_name = model or _default_model()

    def _call() -> Any:
        return client.models.generate_content(
            model=model_name,
            contents=[types.Part.from_text(text=f"{system_prompt}\n\n{user_prompt}")],
            config=types.GenerateContentConfig(
                temperature=temperature,
                response_mime_type="application/json",
            ),
        )

    try:
        fut = _EXECUTOR.submit(_call)
        try:
            resp = fut.result(timeout=timeout_seconds)
        except FutureTimeoutError:
            fut.cancel()
            return {}
    except Exception as e:
        print(f"[gemini_service] generate_json failed: {e}")
        return {}

    try:
        text = (getattr(resp, "text", "") or "").strip()
        return _parse_json_loose(text)
    except Exception as e:
        print(f"[gemini_service] generate_json parse failed: {e}")
        return {}


def generate_vision_json(
    *,
    system_prompt: str,
    user_prompt: str,
    image_base64: str,
    model: Optional[str] = None,
    temperature: float = 0.2,
    timeout_seconds: float = 45.0,
) -> Dict[str, Any]:
    client = _get_client()
    model_name = model or _default_model()

    if image_base64.startswith("data:image"):
        image_base64 = image_base64.split(",", 1)[1]

    try:
        image_bytes = base64.b64decode(image_base64)
    except Exception:
        image_bytes = b""

    def _call() -> Any:
        return client.models.generate_content(
            model=model_name,
            contents=[
                types.Part.from_text(text=f"{system_prompt}\n\n{user_prompt}"),
                types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
            ],
            config=types.GenerateContentConfig(
                temperature=temperature,
                response_mime_type="application/json",
            ),
        )

    try:
        fut = _EXECUTOR.submit(_call)
        try:
            resp = fut.result(timeout=timeout_seconds)
        except FutureTimeoutError:
            fut.cancel()
            return {}
    except Exception as e:
        print(f"[gemini_service] generate_vision_json failed: {e}")
        return {}

    try:
        text = (getattr(resp, "text", "") or "").strip()
        return _parse_json_loose(text)
    except Exception as e:
        print(f"[gemini_service] generate_vision_json parse failed: {e}")
        return {}








