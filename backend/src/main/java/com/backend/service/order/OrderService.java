package com.backend.service.order;

import com.backend.common.dto.PageResponse;
import com.backend.domain.order.OrderStatus;
import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.request.OrderListRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.order.response.OrderSummaryResponse;

import java.time.LocalDate;
import java.util.List;

public interface OrderService {

    OrderCreateFromCartResponse createOrderFromCart(Long memberId, OrderCreateFromCartRequest request);

    OrderDetailResponse getOrderDetailForMember(String orderNo, Long memberId);

    PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, OrderListRequest request);

    OrderDetailResponse getOrderDetailForGuest(String orderNo, String guestPhone, String guestPassword);

    void updateShipToForMember(String orderNo, Long memberId, OrderCreateFromCartRequest.ShipToDto shipToDto);

    List<OrderSummaryResponse> getOrdersByFilters(Long memberId, LocalDate date, String productName, OrderStatus status);
}
