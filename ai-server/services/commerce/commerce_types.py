"""Commerce 세션/상태 타입."""
from typing import Dict, Any, Optional, List
from enum import Enum
from dataclasses import dataclass, field
from datetime import datetime


class CommerceState(str, Enum):
    RECOMMEND = "RECOMMEND"
    CONFIRM_PRODUCT = "CONFIRM_PRODUCT"
    ADD_TO_CART = "ADD_TO_CART"
    CONFIRM_ADDRESS = "CONFIRM_ADDRESS"
    PAYMENT_READY = "PAYMENT_READY"


@dataclass
class SessionData:
    state: CommerceState
    recommendation_condition: Optional[Dict[str, Any]] = None
    recommended_products: list = field(default_factory=list)

    # 이번 턴 기준 확정된 RecommendationCondition (dict 형태로 저장)
    latest_condition: Optional[Dict[str, Any]] = None
    # 정규화된 쿼리 문자열 (semantic embedding 계산용 캐시)
    last_query_text: Optional[str] = None
    # 추천 요청 ID (로깅/AB 테스트용)
    recommendation_id: Optional[str] = None

    selected_product_id: Optional[int] = None
    selected_variant_id: Optional[int] = None
    quantity: int = 1
    address_mode: Optional[str] = None
    address_id: Optional[int] = None

    cart_id: Optional[str] = None
    order_no: Optional[str] = None

    goal_type: Optional[str] = None
    product_category: Optional[str] = None
    member_gender: Optional[str] = None
    member_height_cm: Optional[float] = None
    member_weight_kg: Optional[float] = None
    budget_max: Optional[float] = None

    profile_avoid: List[str] = field(default_factory=list)
    slot_avoid: List[str] = field(default_factory=list)
    must_have: List[str] = field(default_factory=list)
    sort_preference: Optional[str] = None
    keyword: Optional[str] = None
    variant_option: Optional[str] = None
    pending_action: Optional[str] = None
    recipient_name: Optional[str] = None

    last_result_type: Optional[str] = None

    last_updated: datetime = field(default_factory=datetime.now)
    awaiting_since: Optional[datetime] = None
