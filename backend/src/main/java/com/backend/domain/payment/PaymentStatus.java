package com.backend.domain.payment;

public enum PaymentStatus {
    READY,     // 결제 준비 완료 (pay/ready 이후)
    APPROVED,
    FAILED
}

