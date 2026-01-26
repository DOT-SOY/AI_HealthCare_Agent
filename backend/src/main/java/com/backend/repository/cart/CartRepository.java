package com.backend.repository.cart;

import com.backend.domain.cart.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    
    // memberId로 조회
    Optional<Cart> findByMemberId(Long memberId);
    
    // guestToken으로 조회
    Optional<Cart> findByGuestToken(String guestToken);
    
    // memberId로 조회 (N+1 방지: items, items.variant, items.variant.product, items.variant.product.images)
    @EntityGraph(attributePaths = {
        "items",
        "items.variant",
        "items.variant.product",
        "items.variant.product.images"
    })
    Optional<Cart> findWithItemsByMemberId(Long memberId);
    
    // guestToken으로 조회 (N+1 방지: items, items.variant, items.variant.product, items.variant.product.images)
    @EntityGraph(attributePaths = {
        "items",
        "items.variant",
        "items.variant.product",
        "items.variant.product.images"
    })
    Optional<Cart> findWithItemsByGuestToken(String guestToken);
}
