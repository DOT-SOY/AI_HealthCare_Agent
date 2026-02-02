package com.backend.repository.shop;

import com.backend.domain.shop.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

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
    /** true면 품절 상품( variant가 있으면서 모두 재고 0 ) 제외 */
    @Builder.Default
    private boolean excludeOutOfStock = false;
}
