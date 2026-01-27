package com.backend.service.order;

import com.backend.dto.order.response.OrderDetailResponse;

public interface OrderService {

    /**
     * 회원 주문 상세 조회
     */
    OrderDetailResponse getOrderDetailForMember(String orderNo, Long memberId);

    /**
     * 비회원 주문 조회
     */
    OrderDetailResponse getOrderDetailForGuest(String orderNo, String guestPhone, String guestPassword);
}

