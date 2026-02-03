"""
간단한 커머스 플로우 테스트 스크립트

- 실제 OpenAI/Backend/Redis 대신, in-memory + mock 으로 상태머신/오케스트레이션 흐름만 검증.
- `python test_commerce_flows.py` 로 실행하면 각 시나리오 결과를 콘솔에 출력.
"""
import os
from typing import Dict, Any


# OpenAI 클라이언트 import 시 에러 나지 않도록 dummy 키 세팅
os.environ.setdefault("OPENAI_API_KEY", "test-key-for-local-tests")


from schemas.commerce.recommendation_schema import RecommendationCondition
from services.commerce import commerce_orchestration_service as co
from services.commerce.commerce_types import CommerceState, SessionData


# ---- 간단 in-memory 세션 스토어로 state_machine 메서드 monkeypatch ----

_SESSIONS: Dict[str, SessionData] = {}


def _sm_get_session(session_id: str):
    return _SESSIONS.get(session_id)


def _sm_create_session(session_id: str):
    s = SessionData(state=CommerceState.RECOMMEND)
    _SESSIONS[session_id] = s
    return s


def _sm_update_session(session_id: str, **kwargs):
    s = _SESSIONS.get(session_id)
    if not s:
        return None
    for k, v in kwargs.items():
        if hasattr(s, k):
            setattr(s, k, v)
    return s


def _sm_transition_state(session_id: str, new_state: CommerceState):
    s = _SESSIONS.get(session_id)
    if not s:
        return False
    s.state = new_state
    return True


def _sm_delete_session(session_id: str):
    _SESSIONS.pop(session_id, None)


co.state_machine.get_session = _sm_get_session
co.state_machine.create_session = _sm_create_session
co.state_machine.update_session = _sm_update_session
co.state_machine.transition_state = _sm_transition_state
co.state_machine.delete_session = _sm_delete_session


# ---- LLM/Backend 의존 부분 mock ----


def _fake_classify_intent(text: str) -> Dict[str, Any]:
    """글로벌 인텐트: '루틴'이 포함되면 OFF_TOPIC 유도, 그 외에는 PRODUCT_RECOMMEND."""
    if "루틴" in text:
        return {"intent": "ROUTINE_QUERY"}
    return {"intent": "PRODUCT_RECOMMEND"}


co.classify_intent = _fake_classify_intent


def _fake_extract_slots(text: str) -> Dict[str, Any]:
    """
    의도/슬롯 추출 mock.
    - "추천해줘"만 있으면 INFO_LACK 유도용 ALL/None.
    - 그 외에는 다이어트 보충제 시나리오.
    """
    base = {
        "intent": "PRODUCT_RECOMMEND",
        "goal": "DIET",
        "product_category": "SUPPLEMENT",
        "budget": 50000,
        "avoid": [],
        "keyword": "프로틴",
        "variant_option": None,
        "address_mode": None,
        "pending_action": None,
        "recipient_name": None,
        "needs_personalization": True,
    }
    if text.strip() == "추천해줘":
        base["goal"] = "ALL"
        base["product_category"] = "ALL"
        base["budget"] = None
        base["keyword"] = None
        base["needs_personalization"] = False
    return base


co.extract_commerce_intent_and_slots = _fake_extract_slots


def _fake_generate_condition(
    user_text: str,
    extracted_slots: Dict[str, Any],
    auth_token: str | None = None,
    profile_context: Dict[str, Any] | None = None,
) -> RecommendationCondition | None:
    """RAG/LLM 대신 단순 RecommendationCondition 생성."""
    return RecommendationCondition(
        goal=extracted_slots.get("goal", "ALL"),
        product_category=extracted_slots.get("product_category", "ALL"),
        budget_max=extracted_slots.get("budget"),
        avoid=extracted_slots.get("avoid") or [],
        must_have=[],
        priority=[],
        user_profile_used=bool(profile_context),
        derived_constraints={},
        keyword=extracted_slots.get("keyword"),
    )


co.generate_recommendation_condition = _fake_generate_condition


def _fake_call_backend_recommend(condition: RecommendationCondition, auth_token: str) -> Dict[str, Any] | None:
    """Backend /api/products/recommend mock."""
    return {
        "products": [
            {
                "productId": 1,
                "name": "테스트 프로틴",
                "availableVariants": [
                    {"variantId": 10, "name": "기본", "stockQty": 10},
                ],
            }
        ]
    }


co.call_backend_recommend = _fake_call_backend_recommend


# ADD_TO_CART / CONFIRM_ADDRESS / PAYMENT_READY 단계도 외부 HTTP 없이 흉내만 냄


def _fake_handle_add_to_cart_state(session_id: str, auth_token: str | None) -> Dict[str, Any]:
    co.state_machine.transition_state(session_id, CommerceState.CONFIRM_ADDRESS)
    return _fake_handle_confirm_address_state(None, session_id, auth_token)


def _fake_handle_confirm_address_state(
    text: str | None,
    session_id: str,
    auth_token: str | None,
) -> Dict[str, Any]:
    co.state_machine.update_session(session_id, address_id=1)
    co.state_machine.transition_state(session_id, CommerceState.PAYMENT_READY)
    return _fake_handle_payment_ready_state(session_id, auth_token)


def _fake_handle_payment_ready_state(session_id: str, auth_token: str | None) -> Dict[str, Any]:
    co.state_machine.delete_session(session_id)
    return {
        "state": CommerceState.PAYMENT_READY.value,
        "message": "결제 페이지로 이동합니다.",
        "payment_ready": {"dummy": "ok"},
        "order_no": "TEST-ORDER",
        "error": "FLOW_COMPLETED",
    }


co.handle_add_to_cart_state = _fake_handle_add_to_cart_state
co.handle_confirm_address_state = _fake_handle_confirm_address_state
co.handle_payment_ready_state = _fake_handle_payment_ready_state


# ---- 테스트 케이스 ----


def test_happy_path() -> None:
    """RECOMMEND → CONFIRM_PRODUCT → ADD_TO_CART → CONFIRM_ADDRESS → PAYMENT_READY"""
    session_id = "test_happy"
    auth = "Bearer dummy"

    r1 = co.handle_commerce_recommend("다이어트용 프로틴 추천해줘", session_id, auth_token=auth)
    assert r1["state"] == CommerceState.CONFIRM_PRODUCT.value, r1

    r2 = co.handle_commerce_recommend("응", session_id, auth_token=auth)
    assert r2["state"] == CommerceState.PAYMENT_READY.value, r2
    assert r2["error"] == "FLOW_COMPLETED", r2


def test_info_lack_then_recommend() -> None:
    """첫 요청은 INFO_LACK, 추가 정보 제공 후 CONFIRM_PRODUCT로 이어지는지."""
    session_id = "test_info_lack"
    auth = "Bearer dummy"

    r1 = co.handle_commerce_recommend("추천해줘", session_id, auth_token=auth)
    assert r1["state"] == CommerceState.RECOMMEND.value, r1
    assert r1["error"] == "INFO_LACK", r1

    r2 = co.handle_commerce_recommend("다이어트용 보충제", session_id, auth_token=auth)
    assert r2["state"] == CommerceState.CONFIRM_PRODUCT.value, r2


def test_off_topic() -> None:
    """루틴 질문 등 다른 도메인 발화 시 OFF_TOPIC + 세션 삭제."""
    session_id = "test_off_topic"
    auth = "Bearer dummy"

    r1 = co.handle_commerce_recommend("보충제 추천해줘", session_id, auth_token=auth)
    assert r1["state"] == CommerceState.CONFIRM_PRODUCT.value, r1
    assert session_id in _SESSIONS

    r2 = co.handle_commerce_recommend("오늘 루틴 뭐였지?", session_id, auth_token=auth)
    assert r2["error"] == "OFF_TOPIC", r2
    assert session_id not in _SESSIONS


def main() -> None:
    tests = [
        ("happy_path", test_happy_path),
        ("info_lack_then_recommend", test_info_lack_then_recommend),
        ("off_topic", test_off_topic),
    ]

    print("== Commerce flow tests (mocked) ==")
    for name, fn in tests:
        try:
            _SESSIONS.clear()
            fn()
            print(f"[OK]   {name}")
        except AssertionError as e:
            print(f"[FAIL] {name} - AssertionError: {e}")
        except Exception as e:
            print(f"[FAIL] {name} - Exception: {e}")


if __name__ == "__main__":
    main()

