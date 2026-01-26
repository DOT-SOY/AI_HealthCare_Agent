package com.backend.domain.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProductCategoryId implements Serializable {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    protected ProductCategoryId() {}

    public ProductCategoryId(Long productId, Long categoryId) {
        this.productId = productId;
        this.categoryId = categoryId;
    }

    public Long getProductId() { return productId; }
    public Long getCategoryId() { return categoryId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductCategoryId that)) return false;
        return Objects.equals(productId, that.productId) &&
                Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, categoryId);
    }
}
