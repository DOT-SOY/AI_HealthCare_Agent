package com.backend.dto.order.response;

import com.backend.domain.order.OrderBuyerSnapshot;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderBuyerSnapshotResponse {

    private String name;
    private String email;
    private String phone;

    public static OrderBuyerSnapshotResponse from(OrderBuyerSnapshot snapshot) {
        return OrderBuyerSnapshotResponse.builder()
                .name(snapshot.getBuyerName())
                .email(snapshot.getBuyerEmail())
                .phone(snapshot.getBuyerPhone())
                .build();
    }
}

