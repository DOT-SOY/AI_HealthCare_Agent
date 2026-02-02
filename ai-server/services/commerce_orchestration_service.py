"""
Commerce 추천 오케스트레이션 서비스
"""
import os
import time
from typing import Dict, Any, Optional
import httpx
from services.commerce_intent_service import extract_commerce_intent_and_slots
from services.commerce_recommendation_service import generate_recommendation_condition
from services.commerce_state_machine import state_machine, CommerceState, SessionData
from services.commerce_exception_handler import handle_exception, get_user_message_for_error
from schemas.recommendation_schema import RecommendationCondition

# Backend 서버 URL
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")


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
        # goal이 "ALL"이면 null로 변환 (Backend에서 처리)
        goal_value = condition.goal if condition.goal != "ALL" else None
        product_category_value = condition.product_category if condition.product_category != "ALL" else None
        
        request_body = {
            "goal": goal_value,
            "productCategory": product_category_value,
            "budgetMax": condition.budget_max,
            "avoid": condition.avoid,
            "mustHave": condition.must_have,
            "priority": condition.priority
        }
        
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
    try:
        # 1. Intent/Slot 추출
        extracted_slots = extract_commerce_intent_and_slots(text)
        
        # 2. 추천 조건 생성
        condition = generate_recommendation_condition(text, extracted_slots, auth_token)
        if not condition:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "추천 조건을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.",
                "error": "CONDITION_GENERATION_FAILED"
            }
        
        # 3. Backend 상품 추천 호출
        if not auth_token:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "인증이 필요합니다.",
                "error": "AUTH_REQUIRED"
            }
        
        backend_response = call_backend_recommend(condition, auth_token)
        if not backend_response or not backend_response.get("products"):
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
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": get_user_message_for_error("PRODUCT_OUT_OF_STOCK"),
                "error": "PRODUCT_OUT_OF_STOCK"
            }
        
        products = available_products
        
        # 4. 1순위 상품 선택 (코드로 선택)
        selected_product = products[0]
        
        # 5. 세션 업데이트
        state_machine.update_session(
            session_id,
            recommendation_condition=condition.to_dict(),
            recommended_products=products,
            selected_product_id=selected_product.get("productId"),
            selected_variant_id=selected_product.get("availableVariants", [{}])[0].get("variantId") if selected_product.get("availableVariants") else None
        )
        
        # 6. 상태 전이
        state_machine.transition_state(session_id, CommerceState.CONFIRM_PRODUCT)
        
        # 7. 응답 생성
        product_name = selected_product.get("name", "상품")
        message = f"{product_name}을(를) 추천드립니다. 구매하시겠어요?"
        
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
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "세션이 만료되었습니다. 다시 시작해주세요.",
            "error": "SESSION_EXPIRED"
        }
    
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
        # RECOMMEND로 재시작 (조건 1개만 갱신 가능하도록)
        state_machine.transition_state(session_id, CommerceState.RECOMMEND)
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "다른 상품을 추천해드릴까요? 원하시는 조건을 말씀해주세요.",
            "error": None
        }
    else:
        # 명확하지 않은 응답
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
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "세션이 만료되었습니다. 다시 시작해주세요.",
            "error": "SESSION_EXPIRED"
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
        
        if not addresses or len(addresses) == 0:
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": get_user_message_for_error("NO_ADDRESS"),
                "error": "NO_ADDRESS",
                "requires_address_input": True
            }
        
        # 기본 배송지 또는 첫 번째 배송지 사용
        default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        
        # 배송지 주소 문자열 생성
        ship_to_name = default_address.get('shipToName', '')
        address1 = default_address.get('shipAddress1', '')
        address2 = default_address.get('shipAddress2', '')
        address_text = f"{address1} {address2}".strip()
        if ship_to_name:
            address_display = f"{ship_to_name} {address_text}"
        else:
            address_display = address_text
        
        # 사용자 확인이 필요한 경우
        if text:
            text_lower = text.lower().strip()
            positive_responses = ["응", "예", "좋아", "맞아", "그래", "네"]
            if any(pos in text_lower for pos in positive_responses):
                # 배송지 확정
                state_machine.update_session(session_id, address_id=default_address.get("id"))
                state_machine.transition_state(session_id, CommerceState.PAYMENT_READY)
                return handle_payment_ready_state(session_id, auth_token)
            else:
                return {
                    "state": CommerceState.CONFIRM_ADDRESS.value,
                    "message": f"배송지: {address_display}로 배송하시겠어요?",
                    "address": default_address
                }
        else:
            # 배송지 확인 질문
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": f"배송지: {address_display}로 배송하시겠어요?",
                "address": default_address
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
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "세션이 만료되었습니다. 다시 시작해주세요.",
            "error": "SESSION_EXPIRED"
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
            
            return {
                "state": CommerceState.PAYMENT_READY.value,
                "message": "결제 페이지로 이동합니다.",
                "payment_ready": payment_data,
                "order_no": session.order_no
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
            default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        
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
                "recipientName": default_address.get("shipToName"),
                "recipientPhone": default_address.get("shipToPhone"),
                "zipcode": default_address.get("shipZipcode"),
                "address1": default_address.get("shipAddress1"),
                "address2": default_address.get("shipAddress2", "")
            },
            "buyer": {
                "buyerName": profile_data.get("name", ""),
                "buyerEmail": profile_data.get("email", ""),
                "buyerPhone": default_address.get("shipToPhone", "")  # 배송지 전화번호 사용
            }
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
        
        # 세션 업데이트
        state_machine.update_session(session_id, order_no=order_no)
        
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": "결제 페이지로 이동합니다.",
            "payment_ready": payment_data,
            "order_no": order_no
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



