package com.backend.repository.order;

import com.backend.domain.order.Order;
import com.backend.domain.order.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    @EntityGraph(attributePaths = {"items", "items.variant", "buyerSnapshot", "shipToSnapshot"})
    Optional<Order> findDetailByOrderNo(String orderNo);

    @EntityGraph(attributePaths = {"items", "buyerSnapshot", "shipToSnapshot"})
    Optional<Order> findWithDetailsByOrderNo(String orderNo);

    /**
     * 결제 후 후처리(finalizeAfterPaid) 승자 선점을 위한 CAS 쿼리.
     *
     * - 조건:
     *   - 해당 주문이 PAID 상태일 것
     *   - 아직 finalized 되지 않았을 것
     *   - 다른 트랜잭션이 finalizing 중이 아닐 것
     *
     * @return 업데이트된 행 수 (1: 선점 성공, 0: 선점 실패 또는 조건 불충족)
     */
    @Modifying
    @Query("""
            UPDATE Order o
               SET o.finalizing = true
             WHERE o.orderNo = :orderNo
               AND o.status = :paidStatus
               AND o.finalized = false
               AND o.finalizing = false
            """)
    int markFinalizingForPaidOrder(@Param("orderNo") String orderNo,
                                   @Param("paidStatus") OrderStatus paidStatus);

    /**
     * 회원별 주문 목록 경량 조회 (orders만, 서브쿼리 없음). idx_orders_member_created 활용.
     */
    @Query("""
            SELECT o.id as id, o.orderNo as orderNo, o.status as status, o.totalPayableAmount as totalPayableAmount, o.createdAt as createdAt
            FROM Order o
            WHERE o.member.id = :memberId
              AND (:fromDateStart is null OR o.createdAt >= :fromDateStart)
              AND (:toDateEnd is null OR o.createdAt < :toDateEnd)
              AND (:status is null OR o.status = :status)
            """)
    Page<OrderSummaryBaseProjection> findSummaryByMemberId(
            @Param("memberId") Long memberId,
            @Param("fromDateStart") Instant fromDateStart,
            @Param("toDateEnd") Instant toDateEnd,
            @Param("status") OrderStatus status,
            Pageable pageable);

    /**
     * 주문 ID 목록에 대해 첫 상품명·상품 수를 한 번에 조회 (조인/집계 1회, 상관 서브쿼리 없음).
     */
    @Query(value = """
            SELECT m.order_id AS orderId, oi.product_name_snapshot AS firstProductName, c.cnt AS itemCount
            FROM (SELECT order_id, MIN(id) AS mid FROM order_items WHERE order_id IN (:orderIds) GROUP BY order_id) m
            INNER JOIN order_items oi ON oi.order_id = m.order_id AND oi.id = m.mid
            INNER JOIN (SELECT order_id, COUNT(*) AS cnt FROM order_items WHERE order_id IN (:orderIds) GROUP BY order_id) c ON m.order_id = c.order_id
            """,
            nativeQuery = true)
    List<OrderItemSummaryProjection> findOrderItemSummaryByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 날짜, 상품명, 배송 상태로 주문 필터링 조회
     * - 날짜가 null이면 필터링 안 함
     * - 상품명이 null이면 필터링 안 함 (LIKE 검색, %는 파라미터에 포함되어야 함)
     * - 배송 상태가 null이면 필터링 안 함
     */
    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT o FROM Order o " +
           "WHERE o.member.id = :memberId " +
           "AND (:dateStart IS NULL OR o.createdAt >= :dateStart) " +
           "AND (:dateEnd IS NULL OR o.createdAt < :dateEnd) " +
           "AND (:productName IS NULL OR EXISTS (SELECT 1 FROM OrderItem i WHERE i.order.id = o.id AND i.productNameSnapshot LIKE :productName)) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByFilters(
        @Param("memberId") Long memberId,
        @Param("dateStart") Instant dateStart,
        @Param("dateEnd") Instant dateEnd,
        @Param("productName") String productName,
        @Param("status") OrderStatus status
    );
}
