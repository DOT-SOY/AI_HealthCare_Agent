package com.backend.dto.shop.response;

import com.backend.domain.shop.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
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
    
    // TODO: 추후 이미지 도메인 연동 시 추가
    // private List<ProductImageResponse> images;
    
    // TODO: 추후 variant 도메인 연동 시 추가
    // private List<ProductVariantResponse> variants;
}
