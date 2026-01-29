package com.backend.domain.order;

import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_order_items_order_id", columnList = "order_id")
        }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private OrderItemStatus status;

    @Column(name = "product_name_snapshot", length = 200, nullable = false)
    private String productNameSnapshot;

    @Column(name = "variant_snapshot", length = 255)
    private String variantSnapshot;

    @Column(name = "unit_price_snapshot", precision = 18, scale = 2, nullable = false)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "line_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal lineAmount;

    @Builder
    public OrderItem(Order order,
                     Product product,
                     ProductVariant variant,
                     OrderItemStatus status,
                     String productNameSnapshot,
                     String variantSnapshot,
                     BigDecimal unitPriceSnapshot,
                     Integer qty,
                     BigDecimal lineAmount) {
        this.order = order;
        this.product = product;
        this.variant = variant;
        this.status = status;
        this.productNameSnapshot = productNameSnapshot;
        this.variantSnapshot = variantSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.qty = qty;
        this.lineAmount = lineAmount;
    }

    void setOrder(Order order) {
        this.order = order;
    }
}

