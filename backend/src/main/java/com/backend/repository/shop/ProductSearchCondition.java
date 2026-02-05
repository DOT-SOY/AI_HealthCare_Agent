package com.backend.repository.shop;

import com.backend.domain.shop.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ProductSearchCondition {
    private String keyword;
    /** 검색 대상: name(상품명), description(상품내용), all(전체) */
    private String searchType;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private ProductStatus status;
    private String sortBy;
    private String direction;
    @Builder.Default
    private boolean excludeOutOfStock = false;
    private List<String> excludeNameKeywords;
    private List<Long> productIds;
}
