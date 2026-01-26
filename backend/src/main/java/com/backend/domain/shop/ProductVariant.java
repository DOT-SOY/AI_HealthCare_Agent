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

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // SKU 코드
    @Column(nullable = false, length = 80)
    private String sku;

    // 옵션 정보 (JSON 문자열)
    @Lob
    @Column(name = "option_json", nullable = false)
    private String optionJson;

    // 가격 (null이면 product.basePrice 사용)
    @Column(precision = 18, scale = 2)
    private BigDecimal price;

    // 재고 수량
    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    // 활성화 여부
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder
    public ProductVariant(Product product,
                         String sku,
                         String optionJson,
                         BigDecimal price,
                         Integer stockQty,
                         Boolean active) {
        this.product = product;
        this.sku = sku;
        this.optionJson = optionJson;
        this.price = price;
        this.stockQty = (stockQty != null) ? stockQty : 0;
        this.active = (active != null) ? active : true;
    }

    // 재고 수량 변경
    public void updateStock(Integer stockQty) {
        if (stockQty == null || stockQty < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
        this.stockQty = stockQty;
    }

    // 재고 증가
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

    // 실제 가격 조회 (variant 가격이 null이면 상품 기본 가격 반환)
    public BigDecimal resolvePrice() {
        return (this.price != null) ? this.price : this.product.getBasePrice();
    }
}
