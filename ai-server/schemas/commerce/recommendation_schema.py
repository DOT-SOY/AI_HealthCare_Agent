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
    FOOD = "FOOD"
    SUPPLEMENT = "SUPPLEMENT"
    HEALTH_GOODS = "HEALTH_GOODS"
    CLOTHING = "CLOTHING"
    ETC = "ETC"
    ALL = "ALL"


class RecommendationCondition:
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
        keyword: Optional[str] = None,
        search_type: Optional[str] = None,
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
        self.search_type = (search_type or "all").strip().lower()

    def to_dict(self) -> Dict[str, Any]:
        return {
            "goal": self.goal,
            "product_category": self.product_category,
            "budget_max": self.budget_max,
            "avoid": self.avoid,
            "must_have": self.must_have,
            "priority": self.priority,
            "user_profile_used": self.user_profile_used,
            "derived_constraints": self.derived_constraints,
            "keyword": self.keyword,
            "search_type": self.search_type,
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
            keyword=kw,
            search_type=data.get("search_type", "all"),
        )

    def validate(self) -> bool:
        if self.goal not in [g.value for g in Goal]:
            return False
        if self.product_category not in [c.value for c in ProductCategory]:
            return False
        if self.budget_max is not None and self.budget_max < 0:
            return False
        return True

    @property
    def body_parts(self) -> List[str]:
        """derived_constraints에서 body_parts 추출 (없으면 빈 리스트)"""
        if not self.derived_constraints:
            return []
        return self.derived_constraints.get("body_parts") or []

    def to_summary_log(self) -> str:
        """디버깅용 요약 로그 문자열"""
        parts = [f"goal={self.goal}", f"category={self.product_category}"]
        if self.keyword:
            parts.append(f"keyword={self.keyword}")
        if self.must_have:
            parts.append(f"must_have={self.must_have}")
        if self.priority:
            parts.append(f"priority={self.priority}")
        if self.body_parts:
            parts.append(f"body_parts={self.body_parts}")
        if self.avoid:
            parts.append(f"avoid={self.avoid}")
        return ", ".join(parts)

    def to_normalized_query_text(self, user_query: Optional[str] = None) -> str:
        """
        정규화된 쿼리 문자열 생성 (semantic embedding 계산용).
        형식: "goal={goal}; category={category}; must=[...]; avoid=[...]; keyword={keyword}; user_query=\"...\""
        """
        parts = [
            f"goal={self.goal}",
            f"category={self.product_category}",
        ]
        if self.must_have:
            parts.append(f"must={self.must_have}")
        if self.priority:
            parts.append(f"priority={self.priority}")
        if self.body_parts:
            parts.append(f"body_parts={self.body_parts}")
        if self.avoid:
            parts.append(f"avoid={self.avoid}")
        if self.keyword:
            parts.append(f"keyword={self.keyword}")
        if user_query and user_query.strip():
            parts.append(f'user_query="{user_query.strip()}"')
        return "; ".join(parts)
