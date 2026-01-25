package com.backend.dto.shop.response;

import com.backend.domain.shop.ProductStatus;
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
    private Long createdBy; // 작성자 ID
    private List<ProductImageResponse> images;
    
    // TODO: 추후 variant 도메인 연동 시 추가
    // private List<ProductVariantResponse> variants;
}
