package com.backend.dto.cart.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private Long itemId;
    private Long variantId;
    private Integer qty;
    private Long productId;
    private String productName;
    private BigDecimal price; // TODO: variant.price 또는 product.basePrice
    private String optionSummary; // TODO: variant.optionText
    private String primaryImageUrl; // TODO: product.images 중 primaryImage=true인 것의 URL
}
