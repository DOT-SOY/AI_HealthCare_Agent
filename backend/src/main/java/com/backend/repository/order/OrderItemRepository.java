package com.backend.repository.order;

import com.backend.domain.order.OrderItem;
import com.backend.domain.order.OrderItemStatus;
import com.backend.domain.order.OrderStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_Id(Long orderId);

    /** 해당 variant들 중 주문에 사용된 것이 있는지 여부 */
    boolean existsByVariant_IdIn(Collection<Long> variantIds);

    /** 주문에 사용된 variant ID 목록 (삭제 시 제외할 대상) */
    @Query("SELECT DISTINCT oi.variant.id FROM OrderItem oi WHERE oi.variant.id IN :ids")
    List<Long> findVariantIdsReferencedByOrderItems(@Param("ids") Collection<Long> ids);

    /** 회원이 해당 상품을 결제 완료된 주문으로 구매한 이력이 있는지 여부 */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.member.id = :memberId AND oi.product.id = :productId AND oi.order.status IN :orderStatuses AND oi.status = :itemStatus")
    boolean existsByMemberIdAndProductIdAndOrderStatusInAndItemStatus(
            @Param("memberId") Long memberId,
            @Param("productId") Long productId,
            @Param("orderStatuses") List<OrderStatus> orderStatuses,
            @Param("itemStatus") OrderItemStatus itemStatus
    );
}

