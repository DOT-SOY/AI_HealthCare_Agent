package com.backend.dto.payment.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentReadyResponse {

    /**
     * Toss 위젯 orderId에 사용될 값 (주문번호)
     */
    private String orderId;

    /**
     * 결제 금액 (totalPayableAmount)
     */
    private BigDecimal amount;

    /**
     * 주문 이름 (예: 주문 {orderNo})
     */
    private String orderName;
}

