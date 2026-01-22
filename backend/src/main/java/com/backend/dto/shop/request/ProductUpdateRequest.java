package com.backend.dto.shop.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductUpdateRequest {
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal basePrice;

    // TODO: 추후 ProductStatus enum 확장 시 사용
    // private ProductStatus status;
}
