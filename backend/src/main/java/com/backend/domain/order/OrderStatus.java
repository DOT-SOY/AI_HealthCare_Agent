package com.backend.domain.order;

public enum OrderStatus {
    CREATED,           // 주문 생성 (결제 전)
    PAYMENT_PENDING,   // 결제 대기 (pay/ready 완료 후)
    PAID,              // 결제 완료
    SHIPPED,           // 배송중
    DELIVERED,         // 배송완료
    CANCELED           // 취소
}

