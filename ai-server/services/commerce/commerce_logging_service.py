"""Commerce 추천 이벤트 로깅 서비스.

추천/클릭/장바구니/구매 이벤트를 추적하여, 추천 품질 분석 및 AB 테스트에 활용.
현재는 로그 파일/stdout 출력만 제공하며, 향후 분석 DB/이벤트 스트림으로 확장 가능.
"""
import json
import time
from datetime import datetime
from typing import Dict, Any, Optional, List
import logging

# 추천 이벤트 전용 로거 설정
recommendation_logger = logging.getLogger("commerce.recommendation")
recommendation_logger.setLevel(logging.INFO)

# 콘솔 핸들러 (개발용)
_console_handler = logging.StreamHandler()
_console_handler.setFormatter(logging.Formatter(
    "[%(asctime)s] %(levelname)s %(name)s: %(message)s"
))
recommendation_logger.addHandler(_console_handler)


class RecommendationEventType:
    """추천 이벤트 타입."""
    RECOMMENDATION_REQUESTED = "recommendation_requested"
    RECOMMENDATION_GENERATED = "recommendation_generated"
    PRODUCT_SELECTED = "product_selected"
    CART_ADD = "cart_add"
    ORDER_CREATED = "order_created"
    USER_REJECTED = "user_rejected"
    FALLBACK_TRIGGERED = "fallback_triggered"


def log_recommendation_event(
    event_type: str,
    session_id: str,
    recommendation_id: Optional[str] = None,
    member_id: Optional[int] = None,
    condition: Optional[Dict[str, Any]] = None,
    products: Optional[List[Dict[str, Any]]] = None,
    selected_product_id: Optional[int] = None,
    selected_variant_id: Optional[int] = None,
    extra: Optional[Dict[str, Any]] = None,
) -> None:
    """
    추천 이벤트 로깅.
    
    Args:
        event_type: RecommendationEventType 중 하나
        session_id: 세션 ID
        recommendation_id: 추천 요청 ID (로그 연결용)
        member_id: 회원 ID (로그인 사용자)
        condition: 추천 조건 dict
        products: 추천된 상품 리스트 [{productId, name, ...}, ...]
        selected_product_id: 선택된 상품 ID
        selected_variant_id: 선택된 variant ID
        extra: 추가 메타데이터
    """
    event = {
        "event_type": event_type,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "session_id": session_id,
        "recommendation_id": recommendation_id,
        "member_id": member_id,
    }
    
    if condition:
        event["condition"] = {
            "goal": condition.get("goal"),
            "product_category": condition.get("product_category"),
            "keyword": condition.get("keyword"),
            "budget_max": condition.get("budget_max"),
        }
    
    if products:
        event["recommended_product_ids"] = [p.get("productId") for p in products if p.get("productId")]
        event["recommended_count"] = len(products)
    
    if selected_product_id:
        event["selected_product_id"] = selected_product_id
    
    if selected_variant_id:
        event["selected_variant_id"] = selected_variant_id
    
    if extra:
        event["extra"] = extra
    
    # JSON 형식으로 로깅 (향후 로그 분석 시 파싱 용이)
    recommendation_logger.info(json.dumps(event, ensure_ascii=False, default=str))


def log_recommendation_generated(
    session_id: str,
    recommendation_id: str,
    condition: Dict[str, Any],
    products: List[Dict[str, Any]],
    member_id: Optional[int] = None,
    latency_ms: Optional[float] = None,
    bucket_info: Optional[Dict[str, int]] = None,
) -> None:
    """
    추천 생성 완료 이벤트 로깅.
    
    Args:
        session_id: 세션 ID
        recommendation_id: 추천 요청 ID
        condition: 추천 조건
        products: 추천된 상품 리스트
        member_id: 회원 ID
        latency_ms: 추천 생성 소요 시간 (ms)
        bucket_info: 버킷 구성 정보 {"bucket_a": 30, "bucket_b": 20, "total": 50}
    """
    extra = {}
    if latency_ms is not None:
        extra["latency_ms"] = round(latency_ms, 2)
    if bucket_info:
        extra["bucket_info"] = bucket_info
    
    log_recommendation_event(
        event_type=RecommendationEventType.RECOMMENDATION_GENERATED,
        session_id=session_id,
        recommendation_id=recommendation_id,
        member_id=member_id,
        condition=condition,
        products=products,
        extra=extra if extra else None,
    )


def log_product_selected(
    session_id: str,
    recommendation_id: str,
    selected_product_id: int,
    selected_variant_id: Optional[int],
    member_id: Optional[int] = None,
) -> None:
    """상품 선택 이벤트 로깅."""
    log_recommendation_event(
        event_type=RecommendationEventType.PRODUCT_SELECTED,
        session_id=session_id,
        recommendation_id=recommendation_id,
        member_id=member_id,
        selected_product_id=selected_product_id,
        selected_variant_id=selected_variant_id,
    )


def log_cart_add(
    session_id: str,
    recommendation_id: Optional[str],
    product_id: int,
    variant_id: Optional[int],
    quantity: int,
    member_id: Optional[int] = None,
) -> None:
    """장바구니 담기 이벤트 로깅."""
    log_recommendation_event(
        event_type=RecommendationEventType.CART_ADD,
        session_id=session_id,
        recommendation_id=recommendation_id,
        member_id=member_id,
        selected_product_id=product_id,
        selected_variant_id=variant_id,
        extra={"quantity": quantity},
    )


def log_user_rejected(
    session_id: str,
    recommendation_id: Optional[str],
    member_id: Optional[int] = None,
    reason: Optional[str] = None,
) -> None:
    """사용자 거절 이벤트 로깅."""
    log_recommendation_event(
        event_type=RecommendationEventType.USER_REJECTED,
        session_id=session_id,
        recommendation_id=recommendation_id,
        member_id=member_id,
        extra={"reason": reason} if reason else None,
    )


# ========== AB 테스트 지원 ==========

class ABTestConfig:
    """
    AB 테스트 설정.
    
    환경변수 또는 설정 파일에서 읽어오도록 확장 가능.
    """
    
    # 현재 활성화된 실험 그룹
    # "control": 기존 로직
    # "treatment_a": 인기 버킷 크기 증가
    # "treatment_b": semantic score 가중치 증가
    active_experiment: str = "control"
    
    # 실험별 파라미터 오버라이드
    experiment_params: Dict[str, Dict[str, Any]] = {
        "control": {},
        "treatment_a": {
            "bucket_popular_size": 40,
            "bucket_new_size": 10,
        },
        "treatment_b": {
            "popularity_group_threshold": 0.1,
        },
    }
    
    @classmethod
    def get_params_for_experiment(cls, experiment_name: Optional[str] = None) -> Dict[str, Any]:
        """실험 그룹에 해당하는 파라미터 반환."""
        exp = experiment_name or cls.active_experiment
        return cls.experiment_params.get(exp, {})


def get_experiment_group(session_id: str) -> str:
    """
    세션 ID 기반으로 실험 그룹 결정 (간단한 해시 기반 분배).
    
    실제 운영에서는 더 정교한 분배 로직 필요:
    - 회원별 일관된 그룹 배정
    - 트래픽 비율 조절
    - 실험 시작/종료 시간 관리
    """
    if not ABTestConfig.active_experiment or ABTestConfig.active_experiment == "control":
        return "control"
    
    # 세션 ID 해시 기반 간단 분배 (50:50)
    hash_val = hash(session_id) % 100
    if hash_val < 50:
        return "control"
    else:
        return ABTestConfig.active_experiment


def log_experiment_assignment(
    session_id: str,
    experiment_group: str,
    recommendation_id: Optional[str] = None,
) -> None:
    """실험 그룹 배정 로깅."""
    recommendation_logger.info(json.dumps({
        "event_type": "experiment_assignment",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "session_id": session_id,
        "recommendation_id": recommendation_id,
        "experiment_group": experiment_group,
    }, ensure_ascii=False))
