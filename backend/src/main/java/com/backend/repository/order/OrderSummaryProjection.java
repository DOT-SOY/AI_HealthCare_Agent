package com.backend.repository.order;

import com.backend.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 회원 주문 목록 조회용 경량 프로젝션. 엔티티·연관관계 미로딩.
 */
public interface OrderSummaryProjection {

    String getOrderNo();
    OrderStatus getStatus();
    BigDecimal getTotalPayableAmount();
    Instant getCreatedAt();
    /** 주문 상품 중 첫 번째 상품명 (미리보기용) */
    String getFirstProductName();
    /** 주문 상품 수 */
    int getItemCount();
}
