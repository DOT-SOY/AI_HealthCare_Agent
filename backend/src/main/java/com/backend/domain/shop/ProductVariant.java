package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_variants")
public class ProductVariant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Lob
    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder
    public ProductVariant(Product product,
                         String optionText,
                         BigDecimal price,
                         Integer stockQty,
                         Boolean active) {
        this.product = product;
        this.optionText = optionText;
        this.price = price;
        this.stockQty = (stockQty != null) ? stockQty : 0;
        this.active = (active != null) ? active : true;
    }

    public void updateDetails(String optionText, BigDecimal price, Integer stockQty, Boolean active) {
        if (optionText != null && !optionText.trim().isEmpty()) {
            this.optionText = optionText.trim();
        }
        this.price = price;
        this.stockQty = (stockQty != null && stockQty >= 0) ? stockQty : this.stockQty;
        this.active = (active != null) ? active : this.active;
    }

    // 재고 수량 변경
    public void updateStock(Integer stockQty) {
        if (stockQty == null || stockQty < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
        this.stockQty = stockQty;
    }

    public void increaseStock(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("증가 수량은 0 이상이어야 합니다.");
        }
        this.stockQty += quantity;
    }

    // 재고 감소
    public void decreaseStock(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("감소 수량은 0 이상이어야 합니다.");
        }
        if (this.stockQty < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.stockQty -= quantity;
    }

    public BigDecimal resolvePrice() {
        return (this.price != null) ? this.price : this.product.getBasePrice();
    }
}
