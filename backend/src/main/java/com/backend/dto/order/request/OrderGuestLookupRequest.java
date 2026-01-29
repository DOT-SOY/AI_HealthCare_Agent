package com.backend.dto.order.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderGuestLookupRequest {

    @NotBlank
    private String orderNo;

    @NotBlank
    private String guestPhone;

    @NotBlank
    private String guestPassword;
}

