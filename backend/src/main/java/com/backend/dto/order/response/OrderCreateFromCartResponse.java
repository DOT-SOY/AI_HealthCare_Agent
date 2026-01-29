package com.backend.dto.order.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderCreateFromCartResponse {

    /** 주문번호 (Toss orderId로 사용) */
    private String orderNo;

    /** 결제 금액 (totalPayableAmount) */
    private BigDecimal amount;

    /** 주문 이름 (예: 주문 ORD-xxx) */
    private String orderName;

    /** 내부 PK (선택) */
    private Long orderId;
}
