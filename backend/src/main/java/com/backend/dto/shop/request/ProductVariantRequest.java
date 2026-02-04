package com.backend.dto.shop.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductVariantRequest {
    private Long id;
    private String optionText;
    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal price;
    @NotNull(message = "재고 수량은 필수입니다")
    private Integer stockQty = 0;
    private Boolean active = true;
}
