package com.backend.dto.cart.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long cartId;
    private Boolean isGuest;
    private List<CartItemResponse> items;
    private CartTotalsResponse totals;
}
