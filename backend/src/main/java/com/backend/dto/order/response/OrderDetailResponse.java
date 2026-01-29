package com.backend.dto.order.response;

import com.backend.domain.order.Order;
import com.backend.domain.order.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class OrderDetailResponse {

    private String orderNo;
    private OrderStatus status;
    private BigDecimal totalItemAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalPayableAmount;
    private Instant createdAt;

    private OrderBuyerSnapshotResponse buyer;
    private OrderShipToSnapshotResponse shipTo;
    private List<OrderItemDetailResponse> items;

    public static OrderDetailResponse from(Order order) {
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .totalItemAmount(order.getTotalItemAmount())
                .shippingFee(order.getShippingFee())
                .totalPayableAmount(order.getTotalPayableAmount())
                .createdAt(order.getCreatedAt())
                .buyer(OrderBuyerSnapshotResponse.from(order.getBuyerSnapshot()))
                .shipTo(OrderShipToSnapshotResponse.from(order.getShipToSnapshot()))
                .items(order.getItems().stream()
                        .map(OrderItemDetailResponse::from)
                        .toList())
                .build();
    }
}

