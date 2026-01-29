package com.backend.dto.shop.response;

import com.backend.domain.shop.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 상품 변형(Variant) 응답 DTO
 * API 설계 원칙에 따라 명확한 필드명과 일관된 응답 구조 사용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
    private Long id;
    private String optionText;
    private BigDecimal price;
    private Integer stockQty;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * ProductVariant 엔티티로부터 ProductVariantResponse를 생성하는 정적 팩토리 메서드
     * 
     * @param variant ProductVariant 엔티티
     * @return ProductVariantResponse
     */
    public static ProductVariantResponse from(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .optionText(variant.getOptionText())
                .price(variant.getPrice())
                .stockQty(variant.getStockQty())
                .active(variant.isActive())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }
}
