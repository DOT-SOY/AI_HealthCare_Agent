package com.backend.dto.order.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderCreateFromCartRequest {

    /**
     * 배송지/수령인 정보
     */
    @NotNull
    @Valid
    private ShipToDto shipTo;

    /**
     * 주문자(구매자) 정보
     */
    @NotNull
    @Valid
    private BuyerDto buyer;

    /** 배송 메모 (선택, MVP에서는 DB 미저장 가능) */
    private String memo;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ShipToDto {
        @NotBlank(message = "수령인 이름은 필수입니다.")
        private String recipientName;

        @NotBlank(message = "수령인 연락처는 필수입니다.")
        private String recipientPhone;

        @NotBlank(message = "우편번호는 필수입니다.")
        private String zipcode;

        @NotBlank(message = "주소는 필수입니다.")
        private String address1;

        private String address2;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BuyerDto {
        @NotBlank(message = "주문자 이름은 필수입니다.")
        private String buyerName;

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String buyerEmail;

        @NotBlank(message = "주문자 연락처는 필수입니다.")
        private String buyerPhone;
    }
}
