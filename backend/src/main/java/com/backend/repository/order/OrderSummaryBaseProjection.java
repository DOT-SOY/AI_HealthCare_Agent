package com.backend.repository.order;

import com.backend.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 회원 주문 목록 조회용 기본 프로젝션 (orders만, 서브쿼리 없음).
 */
public interface OrderSummaryBaseProjection {

    Long getId();
    String getOrderNo();
    OrderStatus getStatus();
    BigDecimal getTotalPayableAmount();
    Instant getCreatedAt();
}
