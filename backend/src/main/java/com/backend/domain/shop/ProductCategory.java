package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {

    // 복합 키
    @EmbeddedId
    private ProductCategoryId id;

    // 상품
    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 카테고리
    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Builder
    public ProductCategory(Product product, Category category) {
        this.product = product;
        this.category = category;
        this.id = new ProductCategoryId(product.getId(), category.getId());
    }
}
