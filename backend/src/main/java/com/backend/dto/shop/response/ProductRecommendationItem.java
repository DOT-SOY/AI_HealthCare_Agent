package com.backend.dto.shop.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 추천 응답 아이템
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationItem {
    
    private Long productId;
    private String name;
    private BigDecimal price;
    private String thumbnailUrl;
    
    // 사용 가능한 변형 목록
    private List<ProductVariantSummary> availableVariants;
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductVariantSummary {
        private Long variantId;
        private String name;
        private Integer stockQty;
    }
}

