package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

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

    // 옵션 정보 (평문 텍스트, 예: "색상: 빨강, 사이즈: L")
    @Lob
    @Column(name = "option_text", nullable = false)
    private String optionText;

    /** DB 컬럼 option_json (NOT NULL). 구조화된 옵션 저장용, 없으면 "{}" */
    @Column(name = "option_json", nullable = false)
    private String optionJson = "{}";

    /** 재고 관리 단위 코드. DB NOT NULL, 미입력 시 UUID로 자동 생성 */
    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

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
                         String optionText,
                         String optionJson,
                         String sku,
                         BigDecimal price,
                         Integer stockQty,
                         Boolean active) {
        this.product = product;
        this.optionText = optionText;
        this.optionJson = (optionJson != null && !optionJson.isBlank()) ? optionJson : "{}";
        this.sku = (sku != null && !sku.isBlank()) ? sku : UUID.randomUUID().toString();
        this.price = price;
        this.stockQty = (stockQty != null) ? stockQty : 0;
        this.active = (active != null) ? active : true;
    }

    /** 옵션 정보/가격/재고/활성 여부 일괄 수정 (주문·장바구니 참조된 옵션 수정용) */
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
