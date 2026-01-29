package com.backend.dto.shop.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 상품 변형(Variant) 생성/수정 요청 DTO
 * API 설계 원칙에 따라 명확한 필드명과 검증 어노테이션 사용
 */
@Getter
@Setter
public class ProductVariantRequest {

    @NotBlank(message = "옵션 정보는 필수입니다")
    private String optionText; // 옵션 평문 (예: "색상: 빨강, 사이즈: L")
    
    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal price; // null이면 상품 기본 가격 사용
    
    @NotNull(message = "재고 수량은 필수입니다")
    private Integer stockQty = 0;
    
    private Boolean active = true; // 기본값 true
}
