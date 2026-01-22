package com.backend.repository.shop.products;

import com.backend.domain.shop.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductSearchCondition {
    private String keyword;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private ProductStatus status;
    private String sortBy;
    private String direction;
}
