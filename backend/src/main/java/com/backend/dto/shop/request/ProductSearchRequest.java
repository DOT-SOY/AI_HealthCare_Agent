package com.backend.dto.shop.request;

import com.backend.domain.shop.ProductStatus;
import com.backend.repository.shop.products.ProductSearchCondition;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchRequest {
    // 검색어
    private String keyword;

    // 카테고리 필터
    private Long categoryId;

    // 가격 범위 필터
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // 상태 필터
    private ProductStatus status;

    // 정렬 기준 (createdAt, basePrice, popularity)
    private String sortBy = "createdAt";
    
    // 정렬 방향 (ASC, DESC)
    private String direction = "DESC";

    public ProductSearchCondition toCondition() {
        return ProductSearchCondition.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(status)
                .sortBy(sortBy)
                .direction(direction)
                .build();
    }
}
