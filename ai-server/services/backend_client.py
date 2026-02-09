"""
Backend API 클라이언트
"""
import os
import time
from typing import Dict, Any, Optional
import httpx

# Backend 서버 URL
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")

# 프로필 단기 캐시 (동일 플로우 내 중복 호출 방지, TTL 60초, 메모리 최소)
_PROFILE_CACHE: Dict[str, tuple] = {}
_PROFILE_CACHE_TTL_SEC = 60


def get_user_profile(auth_token: str) -> Optional[Dict[str, Any]]:
    """
    사용자 프로필 조회 (동일 토큰 60초 내 재호출 시 캐시 반환)
    
    Args:
        auth_token: Authorization 헤더에 사용할 토큰 (Bearer 토큰 전체 또는 토큰만)
    
    Returns:
        사용자 프로필 딕셔너리 또는 None (실패 시)
        {
            "heightCm": 175.0,
            "weightKg": 70.0,
            "bodyFatPercent": 15.5,
            "bodyWaterPercent": 60.0,
            "goal": "DIET",
            "allergies": ["대두"],
            "avoid": ["카페인"]
        }
    """
    try:
        # 토큰이 "Bearer "로 시작하지 않으면 추가
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        cache_key = (auth_token or "")[:80]
        now = time.time()
        if cache_key in _PROFILE_CACHE:
            expiry_ts, data = _PROFILE_CACHE[cache_key]
            if now < expiry_ts:
                return data
            del _PROFILE_CACHE[cache_key]
        
        url = f"{BACKEND_BASE_URL}/api/members/me/profile"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        
        with httpx.Client(timeout=10.0) as client:
            response = client.get(url, headers=headers)
            response.raise_for_status()
            data = response.json()
        
        _PROFILE_CACHE[cache_key] = (now + _PROFILE_CACHE_TTL_SEC, data)
        return data
    
    except httpx.HTTPStatusError as e:
        print(f"Backend 프로필 조회 실패 (HTTP {e.response.status_code}): {e}")
        return None
    except httpx.RequestError as e:
        print(f"Backend 프로필 조회 실패 (네트워크 오류): {e}")
        return None
    except Exception as e:
        print(f"Backend 프로필 조회 실패: {e}")
        return None

