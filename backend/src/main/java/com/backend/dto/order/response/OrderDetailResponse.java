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
        BigDecimal totalPayable = order.getTotalPayableAmount();
        BigDecimal shippingFee = order.getShippingFee();
        BigDecimal totalItemAmount = (totalPayable != null && shippingFee != null)
                ? totalPayable.subtract(shippingFee)
                : null;
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .totalItemAmount(totalItemAmount)
                .shippingFee(shippingFee)
                .totalPayableAmount(totalPayable)
                .createdAt(order.getCreatedAt())
                .buyer(order.getBuyerSnapshot() != null
                        ? OrderBuyerSnapshotResponse.from(order.getBuyerSnapshot())
                        : null)
                .shipTo(order.getShipToSnapshot() != null
                        ? OrderShipToSnapshotResponse.from(order.getShipToSnapshot())
                        : null)
                .items(order.getItems() != null
                        ? order.getItems().stream()
                                .map(OrderItemDetailResponse::from)
                                .toList()
                        : List.of())
                .build();
    }
}

