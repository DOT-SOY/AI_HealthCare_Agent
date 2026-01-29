package com.backend.service.order;

import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;

public interface OrderService {

    /**
     * 장바구니 기준 회원 주문 생성 (from-cart)
     * - 로그인 회원의 장바구니 조회, 상품/옵션·가격·재고 검증 후 Order + OrderItem + 스냅샷 생성, OrderStatus = CREATED
     */
    OrderCreateFromCartResponse createOrderFromCart(Long memberId, OrderCreateFromCartRequest request);

    /**
     * 회원 주문 상세 조회
     */
    OrderDetailResponse getOrderDetailForMember(String orderNo, Long memberId);

    /**
     * 비회원 주문 조회
     */
    OrderDetailResponse getOrderDetailForGuest(String orderNo, String guestPhone, String guestPassword);
}

