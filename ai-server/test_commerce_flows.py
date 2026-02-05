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


# CONFIRM_* 상태에서 문장형 발화 시 호출하는 상위 의도 분류 (classify_intent_top_level)
co.classify_intent_top_level = _fake_classify_intent


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
    """CONFIRM_PRODUCT 상태에서 루틴 질문 시 OFF_TOPIC + 세션 삭제."""
    session_id = "test_off_topic"
    auth = "Bearer dummy"

    r1 = co.handle_commerce_recommend("보충제 추천해줘", session_id, auth_token=auth)
    assert r1["state"] == CommerceState.CONFIRM_PRODUCT.value, r1
    assert session_id in _SESSIONS

    r2 = co.handle_commerce_recommend("오늘 루틴 뭐였지?", session_id, auth_token=auth)
    assert r2["error"] == "OFF_TOPIC", r2
    assert session_id not in _SESSIONS


def test_recommend_state_off_topic() -> None:
    """RECOMMEND 상태에서 문장형 비커머스 발화 시 OFF_TOPIC + 세션 삭제 (시나리오 4)."""
    session_id = "test_recommend_off_topic"
    auth = "Bearer dummy"

    # RECOMMEND 상태에서 바로 "오늘 루틴 뭐였지?" 입력
    r = co.handle_commerce_recommend("오늘 루틴 뭐였지?", session_id, auth_token=auth)
    assert r["error"] == "OFF_TOPIC", r
    assert r.get("intent") == "ROUTINE_QUERY", r
    assert session_id not in _SESSIONS


def main() -> None:
    tests = [
        ("happy_path", test_happy_path),
        ("info_lack_then_recommend", test_info_lack_then_recommend),
        ("off_topic", test_off_topic),
        ("recommend_state_off_topic", test_recommend_state_off_topic),
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


# ---- 키워드 분류 테스트 ----

def test_classify_keywords() -> None:
    """core_keywords 분류 로직 테스트 (부위/상품유형/영양 분리)"""
    from services.commerce.commerce_recommendation_service import _classify_keywords
    
    # 테스트 1: 하체 보호대 키워드
    result1 = _classify_keywords(["하체", "보호대"])
    assert "하체" in result1["body_parts"], f"하체는 body_parts에 있어야 함: {result1}"
    assert "보호대" in result1["type_must"], f"보호대는 type_must에 있어야 함: {result1}"
    assert len(result1["priority"]) == 0, f"priority는 비어있어야 함: {result1}"
    print(f"  [케이스1] 하체 보호대: {result1}")
    
    # 테스트 2: 다이어트 음식 (단백질/식이섬유)
    result2 = _classify_keywords(["단백질", "식이섬유", "다이어트"])
    assert "단백질" in result2["priority"], f"단백질은 priority에 있어야 함: {result2}"
    assert "식이섬유" in result2["priority"], f"식이섬유는 priority에 있어야 함: {result2}"
    assert "다이어트" in result2["priority"], f"다이어트는 priority에 있어야 함: {result2}"
    print(f"  [케이스2] 다이어트 음식: {result2}")
    
    # 테스트 3: 덤벨 키워드
    result3 = _classify_keywords(["덤벨"])
    assert "덤벨" in result3["type_must"], f"덤벨은 type_must에 있어야 함: {result3}"
    print(f"  [케이스3] 덤벨: {result3}")
    
    # 테스트 4: 무릎 보호대 (부위+유형 복합)
    result4 = _classify_keywords(["무릎 보호대"])
    # "무릎 보호대"는 상품 유형("보호대")을 포함하므로 type_must에 있어야 함
    assert "무릎 보호대" in result4["type_must"], f"무릎 보호대는 type_must에 있어야 함: {result4}"
    print(f"  [케이스4] 무릎 보호대: {result4}")
    
    # 테스트 5: 혼합 케이스 (손목 + 스트랩 + 벌크업)
    result5 = _classify_keywords(["손목", "스트랩", "벌크업"])
    assert "손목" in result5["body_parts"], f"손목은 body_parts에 있어야 함: {result5}"
    assert "스트랩" in result5["type_must"], f"스트랩은 type_must에 있어야 함: {result5}"
    assert "벌크업" in result5["priority"], f"벌크업은 priority에 있어야 함: {result5}"
    print(f"  [케이스5] 손목+스트랩+벌크업: {result5}")


def test_recommendation_condition_body_parts() -> None:
    """RecommendationCondition의 body_parts 속성 테스트"""
    
    # body_parts가 없는 경우
    cond1 = RecommendationCondition(
        goal="DIET",
        product_category="HEALTH_GOODS",
    )
    assert cond1.body_parts == [], f"body_parts가 없으면 빈 리스트: {cond1.body_parts}"
    
    # body_parts가 있는 경우
    cond2 = RecommendationCondition(
        goal="ALL",
        product_category="HEALTH_GOODS",
        derived_constraints={"body_parts": ["하체", "무릎"]},
    )
    assert cond2.body_parts == ["하체", "무릎"], f"body_parts가 있으면 해당 리스트: {cond2.body_parts}"
    
    # to_summary_log 테스트
    log = cond2.to_summary_log()
    assert "body_parts" in log, f"summary_log에 body_parts 포함: {log}"
    print(f"  summary_log: {log}")


if __name__ == "__main__":
    main()
    
    print("\n== Keyword classification tests ==")
    try:
        test_classify_keywords()
        print("[OK]   test_classify_keywords")
    except AssertionError as e:
        print(f"[FAIL] test_classify_keywords - {e}")
    except Exception as e:
        print(f"[FAIL] test_classify_keywords - Exception: {e}")
    
    try:
        test_recommendation_condition_body_parts()
        print("[OK]   test_recommendation_condition_body_parts")
    except AssertionError as e:
        print(f"[FAIL] test_recommendation_condition_body_parts - {e}")
    except Exception as e:
        print(f"[FAIL] test_recommendation_condition_body_parts - Exception: {e}")

