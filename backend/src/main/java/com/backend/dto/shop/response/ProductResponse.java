package com.backend.dto.shop.response;

import com.backend.domain.shop.ProductStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private ProductStatus status;
    private BigDecimal basePrice;
    private Instant createdAt;
    private Instant updatedAt;
    
    // TODO: 추후 이미지 도메인 연동 시 추가
    // private List<ProductImageResponse> images;
    
    // TODO: 추후 variant 도메인 연동 시 추가
    // private List<ProductVariantResponse> variants;
    
    // TODO: 추후 member 도메인 추가 시 권한 체크 후 포함
    // private Long createdBy;
}
