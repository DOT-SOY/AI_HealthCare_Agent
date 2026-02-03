"""
추천 조건 JSON 스키마 정의
"""
from typing import List, Optional, Dict, Any
from enum import Enum


class Goal(str, Enum):
    """운동 목적"""
    DIET = "DIET"
    MAINTAIN = "MAINTAIN"
    BULK_UP = "BULK_UP"
    ALL = "ALL"


class ProductCategory(str, Enum):
    """상품 카테고리"""
    FOOD = "FOOD"
    SUPPLEMENT = "SUPPLEMENT"
    HEALTH_GOODS = "HEALTH_GOODS"
    CLOTHING = "CLOTHING"
    ETC = "ETC"
    ALL = "ALL"


class RecommendationCondition:
    """
    추천 조건 JSON 스키마

    예시:
    {
        "goal": "DIET",
        "product_category": "SUPPLEMENT",
        "budget_max": 50000,
        "avoid": ["카페인", "알러지_대두"],
        "must_have": ["단백질", "식이섬유"],
        "priority": ["칼로리_낮음", "단백질_높음"],
        "user_profile_used": true,
        "derived_constraints": {
            "avoid": ["알러지_대두", "카페인"],
            "reason": "사용자 프로필에서 알러지 정보 반영"
        }
    }
    """

    def __init__(
        self,
        goal: str,
        product_category: str,
        budget_max: Optional[float] = None,
        avoid: Optional[List[str]] = None,
        must_have: Optional[List[str]] = None,
        priority: Optional[List[str]] = None,
        user_profile_used: bool = False,
        derived_constraints: Optional[Dict[str, Any]] = None,
        keyword: Optional[str] = None
    ):
        self.goal = goal
        self.product_category = product_category
        self.budget_max = budget_max
        self.avoid = avoid or []
        self.must_have = must_have or []
        self.priority = priority or []
        self.user_profile_used = user_profile_used
        self.derived_constraints = derived_constraints or {}
        self.keyword = (keyword or "").strip() or None

    def to_dict(self) -> Dict[str, Any]:
        """딕셔너리로 변환"""
        return {
            "goal": self.goal,
            "product_category": self.product_category,
            "budget_max": self.budget_max,
            "avoid": self.avoid,
            "must_have": self.must_have,
            "priority": self.priority,
            "user_profile_used": self.user_profile_used,
            "derived_constraints": self.derived_constraints,
            "keyword": self.keyword
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "RecommendationCondition":
        """딕셔너리에서 생성 (키 없으면 기본값)"""
        kw = data.get("keyword")
        if kw is not None and isinstance(kw, str):
            kw = kw.strip() or None
        return cls(
            goal=data.get("goal", "ALL"),
            product_category=data.get("product_category", "ALL"),
            budget_max=data.get("budget_max"),
            avoid=data.get("avoid", []),
            must_have=data.get("must_have", []),
            priority=data.get("priority", []),
            user_profile_used=data.get("user_profile_used", False),
            derived_constraints=data.get("derived_constraints", {}),
            keyword=kw
        )

    def validate(self) -> bool:
        """스키마 유효성 검증"""
        if self.goal not in [g.value for g in Goal]:
            return False
        if self.product_category not in [c.value for c in ProductCategory]:
            return False
        if self.budget_max is not None and self.budget_max < 0:
            return False
        return True
