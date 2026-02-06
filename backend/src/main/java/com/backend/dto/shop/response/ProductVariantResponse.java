package com.backend.dto.shop.response;

import com.backend.domain.shop.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

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
