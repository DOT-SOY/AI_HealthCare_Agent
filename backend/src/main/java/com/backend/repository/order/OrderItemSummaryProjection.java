package com.backend.repository.order;

/**
 * 주문별 상품 요약 (첫 상품명, 상품 수). order_items 집계 1회 쿼리 결과.
 */
public interface OrderItemSummaryProjection {

    Long getOrderId();
    String getFirstProductName();
    int getItemCount();
}
