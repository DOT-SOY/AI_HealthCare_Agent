package com.backend.service.order;

import com.backend.common.dto.PageResponse;
import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.request.OrderListRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.order.response.OrderSummaryResponse;

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
     * 회원 본인 주문 목록 조회 (페이지네이션, 기간·상태 필터)
     */
    PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, OrderListRequest request);

    /**
     * 비회원 주문 조회
     */
    OrderDetailResponse getOrderDetailForGuest(String orderNo, String guestPhone, String guestPassword);

    /**
     * 회원 주문 배송지 스냅샷 수정
     */
    void updateShipToForMember(String orderNo, Long memberId, OrderCreateFromCartRequest.ShipToDto shipToDto);
}

