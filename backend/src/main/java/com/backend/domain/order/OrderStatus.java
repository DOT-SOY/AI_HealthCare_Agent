package com.backend.domain.order;

public enum OrderStatus {
    CREATED,           // 주문 생성 (결제 전)
    PAID,              // 결제 완료
    READY_FOR_SHIPMENT,
    SHIPPED,
    DELIVERED,
    CANCELED
}

