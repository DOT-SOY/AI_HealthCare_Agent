package com.backend.dto.payment.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentReadyResponse {

    /**
     * Toss 위젯 orderId에 사용될 값 (주문번호)
     */
    private String orderId;

    /**
     * 결제 금액 (totalPayableAmount)
     */
    private BigDecimal amount;

    /**
     * 주문 이름 (예: 주문 {orderNo})
     */
    private String orderName;

    /**
     * Toss 위젯 초기화용 클라이언트 키
     */
    private String clientKey;

    /**
     * 토스 결제위젯 v2 customerKey (구매자 식별자).
     * - 회원: "m_" + memberId (예: "m_12345") — long을 2~50자 규격에 맞춤
     * - 비회원: 장바구니 식별용 guest_token(UUID) (예: "cf7b90cd-918b-46e7-9e05-76fa0144426f")
     * 토스 규칙: 2~50자, 영문대소문자·숫자·특수문자(- _ = . @)만 허용.
     */
    private String customerKey;
}

