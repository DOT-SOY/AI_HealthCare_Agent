package com.backend.dto.shop.request;

import com.backend.domain.shop.ProductStatus;
import com.backend.repository.shop.ProductSearchCondition;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSearchRequest {
    private String keyword;
    private String searchType;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private ProductStatus status;
    private String sortBy = "createdAt";
    private String direction = "DESC";

    public ProductSearchCondition toCondition() {
        return ProductSearchCondition.builder()
                .keyword(keyword)
                .searchType(searchType)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(status)
                .sortBy(sortBy)
                .direction(direction)
                .build();
    }
}
