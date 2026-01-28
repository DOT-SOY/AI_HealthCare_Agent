package com.backend.dto.order.response;

import com.backend.domain.order.OrderItem;
import com.backend.domain.order.OrderItemStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderItemDetailResponse {

    private Long id;
    private OrderItemStatus status;
    private String productName;
    private String variantOption;
    private BigDecimal unitPrice;
    private Integer qty;
    private BigDecimal lineAmount;

    public static OrderItemDetailResponse from(OrderItem item) {
        return OrderItemDetailResponse.builder()
                .id(item.getId())
                .status(item.getStatus())
                .productName(item.getProductNameSnapshot())
                .variantOption(item.getVariantSnapshot())
                .unitPrice(item.getUnitPriceSnapshot())
                .qty(item.getQty())
                .lineAmount(item.getLineAmount())
                .build();
    }
}

