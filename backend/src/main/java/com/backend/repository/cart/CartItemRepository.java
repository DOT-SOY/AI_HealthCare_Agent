package com.backend.repository.cart;

import com.backend.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    
    // cartId와 variantId로 조회
    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);
    
    // cartId로 전체 조회
    List<CartItem> findAllByCartId(Long cartId);
    
    // cartId로 삭제
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);
}
