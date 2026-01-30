package com.backend.dto.order.response;

import com.backend.domain.order.Order;
import com.backend.domain.order.OrderStatus;
import com.backend.repository.order.OrderItemSummaryProjection;
import com.backend.repository.order.OrderSummaryBaseProjection;
import com.backend.repository.order.OrderSummaryProjection;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class OrderSummaryResponse {

    private String orderNo;
    private OrderStatus status;
    private BigDecimal totalPayableAmount;
    private Instant createdAt;
    /** 주문 상품 중 첫 번째 상품명 (미리보기용) */
    private String firstProductName;
    /** 주문 상품 수 */
    private int itemCount;

    public static OrderSummaryResponse from(Order order) {
        String first = order.getItems().isEmpty() ? null : order.getItems().get(0).getProductNameSnapshot();
        return OrderSummaryResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .totalPayableAmount(order.getTotalPayableAmount())
                .createdAt(order.getCreatedAt())
                .firstProductName(first)
                .itemCount(order.getItems().size())
                .build();
    }

    public static OrderSummaryResponse from(OrderSummaryProjection projection) {
        return OrderSummaryResponse.builder()
                .orderNo(projection.getOrderNo())
                .status(projection.getStatus())
                .totalPayableAmount(projection.getTotalPayableAmount())
                .createdAt(projection.getCreatedAt())
                .firstProductName(projection.getFirstProductName())
                .itemCount(projection.getItemCount())
                .build();
    }

    /** 주문 기본 프로젝션 + 상품 요약 병합 (상품 요약 없으면 firstProductName=null, itemCount=0) */
    public static OrderSummaryResponse from(OrderSummaryBaseProjection base, OrderItemSummaryProjection itemSummary) {
        return OrderSummaryResponse.builder()
                .orderNo(base.getOrderNo())
                .status(base.getStatus())
                .totalPayableAmount(base.getTotalPayableAmount())
                .createdAt(base.getCreatedAt())
                .firstProductName(itemSummary != null ? itemSummary.getFirstProductName() : null)
                .itemCount(itemSummary != null ? itemSummary.getItemCount() : 0)
                .build();
    }
}
