package com.backend.repository.order;

import com.backend.domain.order.OrderItem;
import com.backend.domain.order.OrderItemStatus;
import com.backend.domain.order.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_Id(Long orderId);

    /** 회원이 해당 상품을 결제 완료된 주문으로 구매한 이력이 있는지 여부 */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.member.id = :memberId AND oi.product.id = :productId AND oi.order.status IN :orderStatuses AND oi.status = :itemStatus")
    boolean existsByMemberIdAndProductIdAndOrderStatusInAndItemStatus(
            @Param("memberId") Long memberId,
            @Param("productId") Long productId,
            @Param("orderStatuses") List<OrderStatus> orderStatuses,
            @Param("itemStatus") OrderItemStatus itemStatus
    );
}

