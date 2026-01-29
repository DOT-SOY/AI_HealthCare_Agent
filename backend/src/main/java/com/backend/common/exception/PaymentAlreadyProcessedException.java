package com.backend.common.exception;

import lombok.Getter;

/**
 * 토스 결제 승인 API에서 ALREADY_PROCESSED_PAYMENT(이미 처리된 결제) 응답 시 사용.
 * confirmTossPayment에서 잡아 멱등 성공 응답으로 처리한다.
 */
@Getter
public class PaymentAlreadyProcessedException extends RuntimeException {
    private final String orderId;

    public PaymentAlreadyProcessedException(String orderId) {
        super("이미 처리된 결제입니다. orderId=" + orderId);
        this.orderId = orderId;
    }
}
