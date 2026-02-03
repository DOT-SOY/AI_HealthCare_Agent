"""
Commerce 세션/상태 공통 타입 (순환 임포트 방지용 분리).
"""
from typing import Dict, Any, Optional, List
from enum import Enum
from dataclasses import dataclass, field
from datetime import datetime


class CommerceState(str, Enum):
    """상태 정의"""
    RECOMMEND = "RECOMMEND"
    CONFIRM_PRODUCT = "CONFIRM_PRODUCT"
    ADD_TO_CART = "ADD_TO_CART"
    CONFIRM_ADDRESS = "CONFIRM_ADDRESS"
    PAYMENT_READY = "PAYMENT_READY"


@dataclass
class SessionData:
    """
    세션 데이터.
    - 슬롯·keyword·variant_option은 recommendation_condition 등으로 저장되며 세션 TTL/삭제와 동일 생애주기.
    - goal_type, member_* , budget_max, profile_avoid 필드는 한 세션 동안 조회한 회원/프로필 정보를 캐시하는 용도로도 사용된다.
    """
    state: CommerceState
    recommendation_condition: Optional[Dict[str, Any]] = None
    recommended_products: list = field(default_factory=list)

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
