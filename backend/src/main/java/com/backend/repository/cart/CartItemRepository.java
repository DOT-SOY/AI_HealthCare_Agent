package com.backend.repository.cart;

import com.backend.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // cartId와 variantId로 조회
    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);

    /** 해당 variant들 중 장바구니에 담긴 것이 있는지 여부 */
    boolean existsByVariant_IdIn(Collection<Long> variantIds);

    /** 장바구니에 담긴 variant ID 목록 (삭제 시 제외할 대상) */
    @Query("SELECT DISTINCT ci.variant.id FROM CartItem ci WHERE ci.variant.id IN :ids")
    List<Long> findVariantIdsReferencedByCartItems(@Param("ids") Collection<Long> ids);
    
    // cartId로 전체 조회
    List<CartItem> findAllByCartId(Long cartId);
    
    // cartId로 삭제
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}
