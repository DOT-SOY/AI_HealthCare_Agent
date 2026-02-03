"""
Commerce 세션 Redis 저장소 (SSOT).
세션 존재 여부는 Redis 키 존재로만 판단.
"""
import os
import json
from typing import Optional
from datetime import datetime

from .commerce_types import SessionData, CommerceState

_REDIS_CLIENT = None
_KEY_PREFIX = "commerce:session:"
_DEFAULT_TTL_SEC = 1800  # 30분


def _get_redis():
    global _REDIS_CLIENT
    if _REDIS_CLIENT is None:
        import redis
        url = os.getenv("REDIS_URL")
        if url:
            _REDIS_CLIENT = redis.from_url(url, decode_responses=True)
        else:
            host = os.getenv("REDIS_HOST", "localhost")
            port = int(os.getenv("REDIS_PORT", "6379"))
            _REDIS_CLIENT = redis.Redis(host=host, port=port, decode_responses=True)
    return _REDIS_CLIENT


def _session_to_dict(data: SessionData) -> dict:
    return {
        "state": data.state.value,
        "recommendation_condition": data.recommendation_condition,
        "recommended_products": data.recommended_products,
        "selected_product_id": data.selected_product_id,
        "selected_variant_id": data.selected_variant_id,
        "quantity": data.quantity,
        "address_mode": data.address_mode,
        "address_id": data.address_id,
        "cart_id": data.cart_id,
        "order_no": data.order_no,
        "goal_type": data.goal_type,
        "product_category": data.product_category,
        "member_gender": data.member_gender,
        "member_height_cm": data.member_height_cm,
        "member_weight_kg": data.member_weight_kg,
        "budget_max": data.budget_max,
        "profile_avoid": data.profile_avoid,
        "slot_avoid": data.slot_avoid,
        "must_have": data.must_have,
        "sort_preference": data.sort_preference,
        "keyword": data.keyword,
        "variant_option": data.variant_option,
        "pending_action": data.pending_action,
        "recipient_name": data.recipient_name,
        "last_result_type": data.last_result_type,
        "last_updated": data.last_updated.isoformat() if data.last_updated else None,
        "awaiting_since": data.awaiting_since.isoformat() if data.awaiting_since else None,
    }


def _dict_to_session(d: dict) -> SessionData:
    state = CommerceState(d["state"]) if d.get("state") else CommerceState.RECOMMEND
    last_updated = None
    if d.get("last_updated"):
        try:
            last_updated = datetime.fromisoformat(d["last_updated"].replace("Z", "+00:00"))
        except Exception:
            last_updated = datetime.now()
    if last_updated is None:
        last_updated = datetime.now()
    awaiting_since = None
    if d.get("awaiting_since"):
        try:
            awaiting_since = datetime.fromisoformat(d["awaiting_since"].replace("Z", "+00:00"))
        except Exception:
            pass
    return SessionData(
        state=state,
        recommendation_condition=d.get("recommendation_condition"),
        recommended_products=d.get("recommended_products") or [],
        selected_product_id=d.get("selected_product_id"),
        selected_variant_id=d.get("selected_variant_id"),
        quantity=d.get("quantity", 1),
        address_mode=d.get("address_mode"),
        address_id=d.get("address_id"),
        cart_id=d.get("cart_id"),
        order_no=d.get("order_no"),
        goal_type=d.get("goal_type"),
        product_category=d.get("product_category"),
        member_gender=d.get("member_gender"),
        member_height_cm=d.get("member_height_cm"),
        member_weight_kg=d.get("member_weight_kg"),
        budget_max=d.get("budget_max"),
        profile_avoid=d.get("profile_avoid") or [],
        slot_avoid=d.get("slot_avoid") or [],
        must_have=d.get("must_have") or [],
        sort_preference=d.get("sort_preference"),
        keyword=d.get("keyword"),
        variant_option=d.get("variant_option"),
        pending_action=d.get("pending_action"),
        recipient_name=d.get("recipient_name"),
        last_result_type=d.get("last_result_type"),
        last_updated=last_updated,
        awaiting_since=awaiting_since,
    )


def get(session_id: str) -> Optional[SessionData]:
    """세션 조회. 없으면 None. (만료 판정은 하지 않음)"""
    r = _get_redis()
    key = _KEY_PREFIX + session_id
    raw = r.get(key)
    if not raw:
        return None
    try:
        d = json.loads(raw)
        return _dict_to_session(d)
    except Exception:
        return None


def set(session_id: str, data: SessionData, ttl_sec: int = _DEFAULT_TTL_SEC) -> None:
    """세션 저장 및 TTL 설정"""
    r = _get_redis()
    key = _KEY_PREFIX + session_id
    r.setex(key, ttl_sec, json.dumps(_session_to_dict(data), default=str))


def delete(session_id: str) -> None:
    """세션 삭제"""
    r = _get_redis()
    r.delete(_KEY_PREFIX + session_id)
