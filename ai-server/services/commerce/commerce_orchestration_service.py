"""
Commerce 추천 오케스트레이션 서비스
"""
import os
import re
import time
from datetime import datetime
from typing import Dict, Any, Optional, List
import httpx

from services.backend_client import get_user_profile
from services.intent_service import classify_intent
from schemas.commerce.recommendation_schema import RecommendationCondition

from .commerce_intent_service import extract_commerce_intent_and_slots
from .commerce_recommendation_service import generate_recommendation_condition
from .commerce_state_machine import state_machine, CommerceState, SessionData
from .commerce_exception_handler import handle_exception, get_user_message_for_error

# Backend 서버 URL
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")

# 옵션 매칭: 토큰 최소 길이
VARIANT_MATCH_MIN_LEN = 2


def ensure_session_profile(session_id: str, auth_token: Optional[str]) -> None:
    """
    세션의 회원/프로필 캐시 필드가 비어 있고 auth_token이 있을 때만
    Backend 프로필 조회(get_user_profile)를 한 번 수행하여 SessionData를 채운다.

    - SessionData.goal_type, member_gender, member_height_cm, member_weight_kg, budget_max,
      profile_avoid 등을 "세션 단위 프로필 캐시"로 사용한다.
    - 이미 이들 필드 중 하나라도 채워져 있으면 재조회하지 않는다.
    """
    if not auth_token:
        return

    session = state_machine.get_session(session_id)
    if not session:
        return

    # 이미 프로필 컨텍스트가 채워져 있으면 재조회하지 않음
    if any(
        [
            session.goal_type is not None,
            session.member_gender is not None,
            session.member_height_cm is not None,
            session.member_weight_kg is not None,
            session.budget_max is not None,
            bool(getattr(session, "profile_avoid", None)),
        ]
    ):
        return

    profile = get_user_profile(auth_token)
    if not profile:
        return

    # Backend 프로필 스키마를 SessionData 캐시 필드로 매핑
    goal_type = profile.get("goal", session.goal_type)
    member_gender = profile.get("gender", session.member_gender)
    member_height_cm = (
        profile.get("heightCm")
        if profile.get("heightCm") is not None
        else profile.get("height_cm", session.member_height_cm)
    )
    member_weight_kg = (
        profile.get("weightKg")
        if profile.get("weightKg") is not None
        else profile.get("weight_kg", session.member_weight_kg)
    )
    budget_max = profile.get("budgetMax")
    if budget_max is None:
        budget_max = profile.get("budget_max", session.budget_max)

    profile_avoid = list(getattr(session, "profile_avoid", []) or [])
    # backend_client 예시 스키마: allergies / avoid
    allergies = profile.get("allergies") or []
    for a in allergies:
        tag = f"알러지_{a}" if isinstance(a, str) else str(a)
        if tag not in profile_avoid:
            profile_avoid.append(tag)
    for a in profile.get("avoid") or []:
        if a not in profile_avoid:
            profile_avoid.append(a)

    state_machine.update_session(
        session_id,
        goal_type=goal_type,
        member_gender=member_gender,
        member_height_cm=member_height_cm,
        member_weight_kg=member_weight_kg,
        budget_max=budget_max,
        profile_avoid=profile_avoid,
    )


def _pick_variant_id(available_variants: List[Dict], option_keyword: Optional[str]) -> Optional[int]:
    """
    variant_option(발화/슬롯)과 availableVariants.name 매칭.
    복수 토큰(공백/쉼표 분리), 토큰 경계(포함, 최소 2자), 재고 우선. 실패 시 첫 번째 variant.
    """
    if not available_variants:
        return None
    if not option_keyword or not str(option_keyword).strip():
        v = available_variants[0]
        return v.get("variantId")

    tokens = [t.strip() for t in re.split(r"[\s,]+", str(option_keyword).strip()) if len(t.strip()) >= VARIANT_MATCH_MIN_LEN]
    if not tokens:
        return available_variants[0].get("variantId")

    def norm(s: str) -> str:
        return (s or "").strip().lower()

    # 재고 있는 것 우선, 그 다음 매칭
    with_stock = [v for v in available_variants if (v.get("stockQty") or 0) > 0]
    candidates = with_stock if with_stock else available_variants

    for v in candidates:
        name = norm(v.get("name") or "")
        if not name:
            continue
        if any(n in name or name in n for n in [norm(t) for t in tokens]):
            return v.get("variantId")
    # 매칭 없으면 첫 번째 variant (실패 시 UX: 기본 옵션)
    return candidates[0].get("variantId")


def call_backend_recommend(condition: RecommendationCondition, auth_token: str) -> Optional[Dict[str, Any]]:
    """
    Backend 상품 추천 API 호출
    
    Args:
        condition: 추천 조건
        auth_token: 인증 토큰
    
    Returns:
        Backend 응답 또는 None
    """
    try:
        # 토큰이 "Bearer "로 시작하지 않으면 추가
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/products/recommend"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        
        # RecommendationCondition을 Backend 요청 형식으로 변환
        goal_value = condition.goal if condition.goal != "ALL" else None
        product_category_value = condition.product_category if condition.product_category != "ALL" else None
        # keyword 정규화: trim, 연속 공백 하나로
        keyword_value = None
        if getattr(condition, "keyword", None) and str(condition.keyword).strip():
            keyword_value = re.sub(r"\s+", " ", str(condition.keyword).strip())

        request_body = {
            "goal": goal_value,
            "productCategory": product_category_value,
            "budgetMax": condition.budget_max,
            "avoid": condition.avoid,
            "mustHave": condition.must_have,
            "priority": condition.priority
        }
        if keyword_value:
            request_body["keyword"] = keyword_value
        
        with httpx.Client(timeout=10.0) as client:
            response = client.post(url, json=request_body, headers=headers)
            response.raise_for_status()
            return response.json()
    
    except httpx.HTTPStatusError as e:
        print(f"Backend 상품 추천 호출 실패 (HTTP {e.response.status_code}): {e}")
        return None
    except Exception as e:
        print(f"Backend 상품 추천 호출 실패: {e}")
        return None


def handle_commerce_recommend(
    text: str,
    session_id: str,
    auth_token: Optional[str] = None
) -> Dict[str, Any]:
    """
    Commerce 추천 요청 처리
    
    Args:
        text: 사용자 발화
        session_id: 세션 ID
        auth_token: 인증 토큰
    
    Returns:
        응답 딕셔너리
    """
    # 세션 조회 또는 생성
    session = state_machine.get_session(session_id)
    if not session:
        session = state_machine.create_session(session_id)

    # 만료 체크: 만료되었으면 세션 삭제 후 새 세션으로 이어서 처리 (만료 메시지 없이 자연스럽게)
    if session.awaiting_since:
        elapsed = (datetime.now() - session.awaiting_since).total_seconds()
        if elapsed > 180:
            state_machine.delete_session(session_id)
            session = state_machine.create_session(session_id)

    current_state = session.state

    # 상태별 처리
    if current_state == CommerceState.RECOMMEND:
        return handle_recommend_state(text, session_id, auth_token)
    elif current_state == CommerceState.CONFIRM_PRODUCT:
        return handle_confirm_product_state(text, session_id, auth_token)
    elif current_state == CommerceState.ADD_TO_CART:
        return handle_add_to_cart_state(session_id, auth_token)
    elif current_state == CommerceState.CONFIRM_ADDRESS:
        return handle_confirm_address_state(text, session_id, auth_token)
    elif current_state == CommerceState.PAYMENT_READY:
        return handle_payment_ready_state(session_id, auth_token)
    else:
        return {
            "state": current_state.value,
            "message": "알 수 없는 상태입니다.",
            "error": "UNKNOWN_STATE"
        }


def handle_recommend_state(
    text: str,
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """RECOMMEND 상태 처리"""
    t_recommend_start = time.time()
    try:
        # 0. 글로벌 의도 분류로 딴소리 여부 판별 (상품 추천/구매 의도가 아니면 세션 종료)
        global_intent = None
        try:
            global_result = classify_intent(text)
            if isinstance(global_result, dict):
                global_intent = global_result.get("intent")
        except Exception:
            global_intent = None

        if global_intent and global_intent != "PRODUCT_RECOMMEND":
            # 상품 추천 도메인이 아닌 발화로 판단: Commerce 세션 종료 후 OFF_TOPIC 신호 반환
            state_machine.delete_session(session_id)
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "지금 말씀하신 내용은 상품 추천과는 다른 주제 같아요. 다른 도움을 드릴 수 있도록 다시 말씀해 주세요.",
                "error": "OFF_TOPIC",
            }

        # 1. 기존 세션 슬롯 조회
        session = state_machine.get_session(session_id)

        # 2. Intent/Slot 추출 (이번 발화 기준)
        extracted_slots = extract_commerce_intent_and_slots(text)

        # 2-1. 세션 슬롯과 병합 (새 발화 우선)
        prev_goal = getattr(session, "goal_type", None) if session else None
        prev_category = getattr(session, "product_category", None) if session else None
        prev_budget = getattr(session, "budget_max", None) if session else None
        prev_keyword = getattr(session, "keyword", None) if session else None
        prev_variant_option = getattr(session, "variant_option", None) if session else None
        prev_slot_avoid = getattr(session, "slot_avoid", []) if session else []
        prev_address_mode = getattr(session, "address_mode", None) if session else None
        prev_pending_action = getattr(session, "pending_action", None) if session else None
        prev_recipient_name = getattr(session, "recipient_name", None) if session else None

        merged_goal = extracted_slots.get("goal") or prev_goal or "ALL"
        merged_category = extracted_slots.get("product_category") or prev_category or "ALL"
        merged_budget = (
            extracted_slots.get("budget")
            if extracted_slots.get("budget") is not None
            else prev_budget
        )
        merged_keyword = extracted_slots.get("keyword") or prev_keyword
        new_avoid = extracted_slots.get("avoid", []) or []
        merged_slot_avoid = []
        # 이전 slot_avoid + 새 avoid 를 순서 유지하며 중복 제거
        for src in (prev_slot_avoid or []), (new_avoid or []):
            for item in src:
                if item not in merged_slot_avoid:
                    merged_slot_avoid.append(item)
        merged_variant_option = extracted_slots.get("variant_option") or prev_variant_option
        merged_address_mode = extracted_slots.get("address_mode") or prev_address_mode
        merged_pending_action = extracted_slots.get("pending_action") or prev_pending_action
        merged_recipient_name = extracted_slots.get("recipient_name") or prev_recipient_name

        # 2-2. 병합된 슬롯을 세션에 저장 (SSOT)
        state_machine.update_session(
            session_id,
            goal_type=merged_goal,
            product_category=merged_category,
            budget_max=merged_budget,
            keyword=merged_keyword,
            slot_avoid=merged_slot_avoid,
            variant_option=merged_variant_option,
            address_mode=merged_address_mode,
            pending_action=merged_pending_action,
            recipient_name=merged_recipient_name,
        )

        # 2-3. 정보 부족 여부 판별 (목적/카테고리/예산/키워드 모두 없는 경우)
        info_lack = (
            merged_goal == "ALL"
            and merged_category == "ALL"
            and merged_budget is None
            and (not merged_keyword or not str(merged_keyword).strip())
        )
        if info_lack:
            # 최소 질문 1개로 필요한 정보만 요청 (추천/DB 조회는 보류)
            state_machine.update_session(
                session_id,
                last_result_type="INFO_LACK",
                awaiting_since=datetime.now(),
            )
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "어떤 목적과 어떤 종류의 상품을 원하시는지 말씀해 주세요. (예: 다이어트용 보충제, 운동복, 홈트 용품 등)",
                "error": "INFO_LACK",
            }

        # 3. 개인화가 필요한 경우 세션 프로필 캐시 로딩 트리거
        needs_personalization = extracted_slots.get("needs_personalization", False)
        if needs_personalization and auth_token:
            try:
                ensure_session_profile(session_id, auth_token)
            except Exception as e:
                # 프로필 조회 실패는 치명적 오류로 보지 않고 로그만 남김
                print(f"[commerce] ensure_session_profile failed: {e}")

        # ensure_session_profile 호출 이후 최신 세션 재조회 (프로필 캐시 반영)
        session = state_machine.get_session(session_id)

        # 4. 추천 조건 생성 (병합된 슬롯 기준)
        merged_slots = {
            "intent": extracted_slots.get("intent", "PRODUCT_RECOMMEND"),
            "goal": merged_goal,
            "product_category": merged_category,
            "budget": merged_budget,
            "avoid": merged_slot_avoid,
            "keyword": merged_keyword,
            "variant_option": merged_variant_option,
        }

        # 4-1. 기존 RecommendationCondition 재사용 여부 결정
        reuse_condition = False
        existing_condition_dict = getattr(session, "recommendation_condition", None) if session else None
        if existing_condition_dict:
            try:
                prev_cond = RecommendationCondition.from_dict(existing_condition_dict)
                if (
                    prev_cond.goal == merged_goal
                    and prev_cond.product_category == merged_category
                    and prev_cond.budget_max == merged_budget
                    and (prev_cond.keyword or None) == (merged_keyword or None)
                    and (prev_cond.avoid or []) == (merged_slot_avoid or [])
                ):
                    reuse_condition = True
            except Exception:
                reuse_condition = False

        # SessionData 기반 프로필 컨텍스트 구성 (세션에 캐시된 경우에만)
        profile_context = None
        if session and any(
            [
                session.goal_type,
                session.member_gender,
                session.member_height_cm is not None,
                session.member_weight_kg is not None,
                session.budget_max is not None,
                bool(getattr(session, "profile_avoid", None)),
            ]
        ):
            profile_context = {
                "goal_type": session.goal_type,
                "member_gender": session.member_gender,
                "member_height_cm": session.member_height_cm,
                "member_weight_kg": session.member_weight_kg,
                "budget_max": session.budget_max,
                "profile_avoid": getattr(session, "profile_avoid", []) or [],
            }

        if reuse_condition:
            condition = RecommendationCondition.from_dict(existing_condition_dict)
        else:
            condition = generate_recommendation_condition(
                text,
                merged_slots,
                auth_token=auth_token,
                profile_context=profile_context,
            )
        if not condition:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "추천 조건을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.",
                "error": "CONDITION_GENERATION_FAILED"
            }
        
        # 4. Backend 상품 추천 호출
        if not auth_token:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "인증이 필요합니다.",
                "error": "AUTH_REQUIRED"
            }
        
        print(f"[commerce] backend_recommend_request total_since_recommend={time.time() - t_recommend_start:.2f}s")
        backend_response = call_backend_recommend(condition, auth_token)
        if not backend_response or not backend_response.get("products"):
            # 상품 부족: 추천 조건은 있지만 결과가 0건인 경우
            state_machine.update_session(session_id, last_result_type="NO_PRODUCTS")
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": get_user_message_for_error("NO_PRODUCTS_FOUND"),
                "error": "NO_PRODUCTS_FOUND"
            }
        
        products = backend_response["products"]
        if not products:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": get_user_message_for_error("NO_PRODUCTS_FOUND"),
                "error": "NO_PRODUCTS_FOUND"
            }
        
        # 품절 상품 필터링 (availableVariants가 없는 경우)
        available_products = [
            p for p in products 
            if p.get("availableVariants") and len(p.get("availableVariants", [])) > 0
        ]
        
        if not available_products:
            state_machine.update_session(session_id, last_result_type="PRODUCT_OUT_OF_STOCK")
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": get_user_message_for_error("PRODUCT_OUT_OF_STOCK"),
                "error": "PRODUCT_OUT_OF_STOCK"
            }
        
        products = available_products

        # 5. 1순위 상품 선택 + 옵션 매칭 (variant_option → availableVariants.name)
        selected_product = products[0]
        variants = selected_product.get("availableVariants") or []
        variant_option = merged_variant_option
        selected_variant_id = _pick_variant_id(variants, variant_option) if variants else None

        # 6. 세션 업데이트 (선택된 상품/옵션, 슬롯 포함)
        state_machine.update_session(
            session_id,
            recommendation_condition=condition.to_dict(),
            recommended_products=products,
            selected_product_id=selected_product.get("productId"),
            selected_variant_id=selected_variant_id,
        )

        # 7. 항상 한 번은 결제 전 확인: 현재까지의 선택 요약을 보여주고 구매 의사 확인
        state_machine.transition_state(session_id, CommerceState.CONFIRM_PRODUCT)

        # 8. 질문 포함 응답 직전에만 awaiting_since 갱신
        state_machine.update_session(session_id, awaiting_since=datetime.now())

        # 9. 응답 생성 (상품/옵션/배송지 모드 요약)
        product_name = selected_product.get("name", "상품")
        option_name = None
        if selected_variant_id and variants:
            for v in variants:
                if v.get("variantId") == selected_variant_id:
                    option_name = v.get("name")
                    break
        option_part = f" (옵션: {option_name})" if option_name else ""

        address_phrase = ""
        if merged_address_mode == "DEFAULT":
            address_phrase = "기본 배송지로 "
        elif merged_address_mode == "NEW":
            address_phrase = "새로운 배송지로 "

        message = f"{product_name}{option_part}을(를) {address_phrase}결제할까요? (예/아니오)"
        return {
            "state": CommerceState.CONFIRM_PRODUCT.value,
            "message": message,
            "products": [selected_product],
            "recommendation_condition": condition.to_dict()
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "recommend")
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"RECOMMEND 상태 처리 실패: {e}")
        error_info = handle_exception(e, "recommend")
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_confirm_product_state(
    text: str,
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """CONFIRM_PRODUCT 상태 처리"""
    session = state_machine.get_session(session_id)
    if not session:
        state_machine.create_session(session_id)
        return handle_recommend_state(text, session_id, auth_token)

    # 사용자 응답 확인 ("응", "예", "좋아", "구매" 등 긍정 / "아니", "안돼" 등 부정)
    text_lower = text.lower().strip()
    positive_responses = ["응", "예", "좋아", "구매", "살래", "네", "맞아", "그래"]
    negative_responses = ["아니", "안돼", "싫어", "안 할래", "취소"]
    
    is_positive = any(pos in text_lower for pos in positive_responses)
    is_negative = any(neg in text_lower for neg in negative_responses)
    
    if is_positive:
        # 장바구니 담기로 전이
        state_machine.transition_state(session_id, CommerceState.ADD_TO_CART)
        return handle_add_to_cart_state(session_id, auth_token)
    elif is_negative:
        # 사용자가 명시적으로 거절한 경우: 루프를 강제하지 않고 플로우만 리셋
        state_machine.transition_state(session_id, CommerceState.RECOMMEND)
        state_machine.update_session(
            session_id,
            last_result_type="REJECTED_BY_USER",
            awaiting_since=None,  # 질문이 아니므로 awaiting_since 갱신/유지하지 않음
        )
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "알겠습니다. 다른 상품이 필요하시면 언제든지 말씀해주세요.",
            "error": None,
        }
    else:
        state_machine.update_session(session_id, awaiting_since=datetime.now())  # 질문 포함 응답일 때만
        return {
            "state": CommerceState.CONFIRM_PRODUCT.value,
            "message": "구매하시겠어요? (예/아니오)",
            "products": session.recommended_products[:1] if session.recommended_products else []
        }


def handle_add_to_cart_state(
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """ADD_TO_CART 상태 처리"""
    session = state_machine.get_session(session_id)
    if not session or not session.selected_product_id:
        state_machine.create_session(session_id)
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "어떤 상품을 도와드릴까요?",
            "error": None,
        }

    if not auth_token:
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }
    
    try:
        # 장바구니 담기 API 호출 (멱등키 포함)
        idempotency_key = f"{session_id}:{session.selected_product_id}:{session.selected_variant_id}:{int(time.time())}"
        
        # 토큰이 "Bearer "로 시작하지 않으면 추가
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/cart/ai/add-item"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json",
            "Idempotency-Key": idempotency_key
        }
        
        request_body = {
            "productId": session.selected_product_id,
            "variantId": session.selected_variant_id,
            "qty": 1
        }
        
        with httpx.Client(timeout=10.0) as client:
            response = client.post(url, json=request_body, headers=headers)
            response.raise_for_status()
        
        # 상태 전이
        state_machine.transition_state(session_id, CommerceState.CONFIRM_ADDRESS)
        
        return handle_confirm_address_state(None, session_id, auth_token)
    
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 409:  # Conflict (이미 담긴 경우)
            # 이미 담긴 경우에도 다음 단계로 진행
            state_machine.transition_state(session_id, CommerceState.CONFIRM_ADDRESS)
            return handle_confirm_address_state(None, session_id, auth_token)
        else:
            error_info = handle_exception(e, "cart_add")
            return {
                "state": CommerceState.ADD_TO_CART.value,
                "message": error_info["message"],
                "error": error_info["error"]
            }
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "cart_add")
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"장바구니 담기 실패: {e}")
        error_info = handle_exception(e, "cart_add")
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_confirm_address_state(
    text: Optional[str],
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """CONFIRM_ADDRESS 상태 처리"""
    if not auth_token:
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }
    
    try:
        # 세션에서 주소 모드/다음 액션/수취인 후보 확인 (out-of-order 입력 대응)
        session = state_machine.get_session(session_id)
        if not session:
            state_machine.create_session(session_id)
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "어떤 상품을 도와드릴까요?",
                "error": None,
            }
        address_mode = getattr(session, "address_mode", None) if session else None
        pending_action = getattr(session, "pending_action", None) if session else None
        recipient_name = getattr(session, "recipient_name", None) if session else None

        # 배송지 조회
        # 토큰이 "Bearer "로 시작하지 않으면 추가
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/member-addr-info/me"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        
        with httpx.Client(timeout=10.0) as client:
            response = client.get(url, headers=headers)
            response.raise_for_status()
            addresses = response.json()

        # 1) 저장된 주소가 전혀 없는 경우 → 정책상 주문 진행 불가
        if not addresses or len(addresses) == 0:
            state_machine.update_session(session_id, last_result_type="NO_ADDRESS")
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": "저장된 배송지가 없어 AI로 주문을 진행할 수 없어요. 마이페이지에서 배송지를 먼저 등록해 주세요.",
                "error": "NO_ADDRESS",
                "requires_address_input": False
            }

        # 2) 기본 배송지 및 수취인 매칭 주소 후보 선정
        default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        recipient_address = None
        if recipient_name:
            rn_norm = str(recipient_name).strip().lower()
            for addr in addresses:
                name = str(addr.get("shipToName") or "").strip().lower()
                if rn_norm and rn_norm in name:
                    recipient_address = addr
                    break

        # address_mode/recipient_name을 고려하여 이번에 보여줄 후보 주소 결정
        candidate_address = default_address
        if recipient_address is not None:
            candidate_address = recipient_address

        # 배송지 주소 문자열 생성 (후보 주소 기준)
        ship_to_name = candidate_address.get('shipToName', '')
        address1 = candidate_address.get('shipAddress1', '')
        address2 = candidate_address.get('shipAddress2', '')
        address_text = f"{address1} {address2}".strip()
        if ship_to_name:
            address_display = f"{ship_to_name} {address_text}"
        else:
            address_display = address_text

        # 3) NEW 요청인 경우: 새 주소 입력 대신 정책 안내 + 기본/기존 주소 권유
        if address_mode == "NEW":
            state_machine.update_session(session_id, awaiting_since=datetime.now())
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": f"지금은 미리 저장된 배송지로만 보낼 수 있어요. 다른 곳으로 보내시려면 마이페이지에서 배송지를 추가해 주세요.\n기본 배송지({address_display})로 보내실까요?",
                "address": default_address
            }

        # 4) 사용자 응답이 있는 경우: 예/아니오/추가 텍스트 처리
        if text:
            text_lower = text.lower().strip()
            positive_responses = ["응", "예", "좋아", "맞아", "그래", "네"]
            negative_responses = ["아니", "아니요", "싫어", "다른 데", "다른데", "다른 주소", "거긴 말고"]
            if any(pos in text_lower for pos in positive_responses):
                # 배송지 확정
                state_machine.update_session(session_id, address_id=candidate_address.get("id"))
                state_machine.transition_state(session_id, CommerceState.PAYMENT_READY)
                return handle_payment_ready_state(session_id, auth_token)
            if any(neg in text_lower for neg in negative_responses):
                # 부정 응답: 기본 배송지 확인 루프를 끊고 정책/수취인 기준으로 안내
                state_machine.update_session(session_id, last_result_type="ADDRESS_REJECTED")

                # 저장된 주소는 있지만 recipient_name 매칭 주소 없음 or 사용자가 거부한 경우
                if recipient_address is None:
                    # 다른 저장된 주소 선택은 UI에서 처리, AI는 정책 안내 + 기본 배송지 재질문
                    state_machine.update_session(session_id, awaiting_since=datetime.now())
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"기본 배송지 외에 다른 저장된 주소로 보내시려면, 마이페이지에서 주소를 추가해 주세요.\n지금 기본 배송지({address_display})로 보내실까요?",
                        "address": default_address
                    }
                else:
                    # recipient_name 매칭 주소가 있는 경우에도 거부되면, 역시 정책 안내 후 기본으로 유도
                    state_machine.update_session(session_id, awaiting_since=datetime.now())
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"이젠 다른 저장된 배송지로만 보낼 수 있어요. 마이페이지에서 주소를 추가하신 후 다시 시도해 주세요.\n현재 기본 배송지({address_display})로 보내실까요?",
                        "address": default_address
                    }

            # 추가 텍스트: 수취인명으로 해석 시도 (예: "이젠아카데미")
            try:
                extracted = extract_commerce_intent_and_slots(text)
            except Exception:
                extracted = {}
            new_recipient = extracted.get("recipient_name")
            if isinstance(new_recipient, str) and new_recipient.strip():
                rn_norm_new = new_recipient.strip().lower()
                matched_addr = None
                for addr in addresses:
                    name = str(addr.get("shipToName") or "").strip().lower()
                    if rn_norm_new and rn_norm_new in name:
                        matched_addr = addr
                        break
                if matched_addr:
                    # 새 수취인 주소로 재질문
                    ship_to_name2 = matched_addr.get('shipToName', '')
                    addr1b = matched_addr.get('shipAddress1', '')
                    addr2b = matched_addr.get('shipAddress2', '')
                    addr_text2 = f"{addr1b} {addr2b}".strip()
                    if ship_to_name2:
                        addr_disp2 = f"{ship_to_name2} {addr_text2}"
                    else:
                        addr_disp2 = addr_text2

                    state_machine.update_session(
                        session_id,
                        recipient_name=new_recipient.strip(),
                        awaiting_since=datetime.now(),
                    )
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"배송지: {addr_disp2}로 배송하시겠어요?",
                        "address": matched_addr
                    }

        # 5) 최초 진입 또는 예/아니오 외 응답: 후보 주소로 확인 질문
        state_machine.update_session(session_id, awaiting_since=datetime.now())
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": f"배송지: {address_display}로 배송하시겠어요?",
            "address": candidate_address
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "address_check")
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"배송지 확인 실패: {e}")
        error_info = handle_exception(e, "address_check")
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_payment_ready_state(
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """PAYMENT_READY 상태 처리"""
    session = state_machine.get_session(session_id)
    if not session:
        state_machine.create_session(session_id)
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "어떤 상품을 도와드릴까요?",
            "error": None,
        }

    if not auth_token:
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }
    
    try:
        # 이미 주문이 생성되어 있으면 재사용
        if session.order_no:
            # 결제 ready만 다시 호출
            if not auth_token.startswith("Bearer "):
                auth_token = f"Bearer {auth_token}"
            
            headers = {
                "Authorization": auth_token,
                "Content-Type": "application/json"
            }
            
            payment_url = f"{BACKEND_BASE_URL}/api/orders/{session.order_no}/pay/ready"
            with httpx.Client(timeout=10.0) as client:
                payment_response = client.post(payment_url, headers=headers)
                payment_response.raise_for_status()
                payment_data = payment_response.json()

            # 결제 ready 성공: 플로우 완료로 간주하고 세션 종료
            state_machine.delete_session(session_id)

            return {
                "state": CommerceState.PAYMENT_READY.value,
                "message": "결제 페이지로 이동합니다.",
                "payment_ready": payment_data,
                "order_no": session.order_no,
                "error": "FLOW_COMPLETED",
            }
        
        # 1. 주문 생성 (장바구니 기준)
        # 토큰이 "Bearer "로 시작하지 않으면 추가
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        
        # 배송지 정보 가져오기
        address_url = f"{BACKEND_BASE_URL}/api/member-addr-info/me"
        with httpx.Client(timeout=10.0) as client:
            address_response = client.get(address_url, headers=headers)
            address_response.raise_for_status()
            addresses = address_response.json()

        # 주소 목록이 비어 있는 경우 방어적 처리 (정상 플로우에서는 CONFIRM_ADDRESS에서 이미 걸러짐)
        if not addresses:
            return {
                "state": CommerceState.PAYMENT_READY.value,
                "message": "저장된 배송지가 없어 AI로 주문을 진행할 수 없어요. 마이페이지에서 배송지를 먼저 등록해 주세요.",
                "error": "NO_ADDRESS",
            }

        # 기본 배송지 및 세션에 저장된 address_id를 고려하여 최종 배송지 선택
        default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        selected_address = default_address
        selected_address_id = getattr(session, "address_id", None)
        if selected_address_id is not None:
            for addr in addresses:
                if addr.get("id") == selected_address_id:
                    selected_address = addr
                    break
        
        # 주문 생성
        # Member 정보 조회 (buyer 정보용) - 프로필 API에서 가져오기
        profile_url = f"{BACKEND_BASE_URL}/api/members/me/profile"
        with httpx.Client(timeout=10.0) as client:
            profile_response = client.get(profile_url, headers=headers)
            profile_response.raise_for_status()
            profile_data = profile_response.json()
        
        order_url = f"{BACKEND_BASE_URL}/api/orders/from-cart"
        order_request = {
            "shipTo": {
                "recipientName": selected_address.get("shipToName"),
                "recipientPhone": selected_address.get("shipToPhone"),
                "zipcode": selected_address.get("shipZipcode"),
                "address1": selected_address.get("shipAddress1"),
                "address2": selected_address.get("shipAddress2", ""),
            },
            "buyer": {
                "buyerName": profile_data.get("name", ""),
                "buyerEmail": profile_data.get("email", ""),
                "buyerPhone": selected_address.get("shipToPhone", ""),  # 배송지 전화번호 사용
            },
        }
        
        with httpx.Client(timeout=10.0) as client:
            order_response = client.post(order_url, json=order_request, headers=headers)
            order_response.raise_for_status()
            order_data = order_response.json()
            order_no = order_data.get("orderNo")
        
        # 2. 결제 ready 호출
        payment_url = f"{BACKEND_BASE_URL}/api/orders/{order_no}/pay/ready"
        with httpx.Client(timeout=10.0) as client:
            payment_response = client.post(payment_url, headers=headers)
            payment_response.raise_for_status()
            payment_data = payment_response.json()

        # 결제 ready 성공: 플로우 완료로 간주하고 세션 종료
        state_machine.delete_session(session_id)

        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": "결제 페이지로 이동합니다.",
            "payment_ready": payment_data,
            "order_no": order_no,
            "error": "FLOW_COMPLETED",
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "payment_ready")
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"결제 ready 실패: {e}")
        error_info = handle_exception(e, "payment_ready")
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }



