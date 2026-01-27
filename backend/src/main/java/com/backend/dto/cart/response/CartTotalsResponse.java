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
public class CartTotalsResponse {
    private Integer itemCount; // 아이템 라인 수
    private Integer totalQty; // 전체 수량 합계
    private BigDecimal totalPrice; // TODO: 전체 가격 합계
}
