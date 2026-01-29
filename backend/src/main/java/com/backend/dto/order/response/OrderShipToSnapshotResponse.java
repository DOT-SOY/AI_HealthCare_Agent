package com.backend.dto.order.response;

import com.backend.domain.order.OrderShipToSnapshot;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderShipToSnapshotResponse {

    private String recipientName;
    private String recipientPhone;
    private String zipcode;
    private String address1;
    private String address2;

    public static OrderShipToSnapshotResponse from(OrderShipToSnapshot snapshot) {
        return OrderShipToSnapshotResponse.builder()
                .recipientName(snapshot.getRecipientName())
                .recipientPhone(snapshot.getRecipientPhone())
                .zipcode(snapshot.getZipcode())
                .address1(snapshot.getAddress1())
                .address2(snapshot.getAddress2())
                .build();
    }
}

