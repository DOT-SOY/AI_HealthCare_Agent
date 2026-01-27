package com.backend.dto.payment.response;

import com.backend.domain.order.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TossPaymentConfirmResponse {

    private String orderId;
    private OrderStatus orderStatus;
    private BigDecimal amount;
    private Instant approvedAt;

    public static TossPaymentConfirmResponse of(String orderId,
                                                OrderStatus status,
                                                BigDecimal amount,
                                                Instant approvedAt) {
        return TossPaymentConfirmResponse.builder()
                .orderId(orderId)
                .orderStatus(status)
                .amount(amount)
                .approvedAt(approvedAt)
                .build();
    }
}

