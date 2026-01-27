package com.backend.dto.cart.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemAddRequest {
    // variantId 또는 productId 중 하나는 필수
    private Long variantId;
    
    private Long productId;  // 옵션이 없는 제품의 경우 사용
    
    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private Integer qty;
}
