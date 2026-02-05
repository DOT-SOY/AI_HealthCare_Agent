package com.backend.dto.shop.response;

import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private ProductStatus status;
    private BigDecimal basePrice;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy;
    private List<ProductImageResponse> images;
    private List<ProductVariantResponse> variants;
    private List<CategoryResponse> categories;
    private ReviewSummaryResponse reviewSummary;

    // 로그인 회원 기준 리뷰 상태
    @JsonProperty("review_status")
    private ReviewStatus reviewStatus;

    public static ProductResponse from(Product product) {
        if (product == null) {
            return null;
        }
        
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .status(product.getStatus())
                .basePrice(product.getBasePrice())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(product.getCreatedBy() != null ? product.getCreatedBy().getId() : null)
                .images(null)
                .build();
    }
}
