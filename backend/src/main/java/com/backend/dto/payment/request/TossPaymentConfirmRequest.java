package com.backend.dto.payment.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TossPaymentConfirmRequest {

    @NotBlank
    private String paymentKey;

    /**
     * Toss 결제 요청 시 사용한 orderId (우리 쪽 주문번호)
     */
    @NotBlank
    private String orderId;

    /**
     * 결제 금액 (원 단위, 정수)
     */
    @NotNull
    @Min(1)
    private Long amount;
}

