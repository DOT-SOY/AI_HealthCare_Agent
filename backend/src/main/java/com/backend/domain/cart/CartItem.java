package com.backend.domain.cart;

import com.backend.domain.shop.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "cart_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_cart_item_cart_variant",
            columnNames = {"cart_id", "variant_id"}
        )
    }
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer qty = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public CartItem(Cart cart, ProductVariant variant, Integer qty) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.cart = cart;
        this.variant = variant;
        this.qty = qty;
    }

    // 수량 변경
    public void updateQty(Integer qty) {
        if (qty == null || qty < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.qty = qty;
    }

    // 수량 증가
    public void increaseQty(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("증가할 수량은 1 이상이어야 합니다.");
        }
        this.qty += quantity;
    }

    // 수량 감소
    public void decreaseQty(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("감소할 수량은 1 이상이어야 합니다.");
        }
        if (this.qty - quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.qty -= quantity;
    }

    // 장바구니 설정 (양방향 관계 설정용)
    void setCart(Cart cart) {
        this.cart = cart;
    }
}
