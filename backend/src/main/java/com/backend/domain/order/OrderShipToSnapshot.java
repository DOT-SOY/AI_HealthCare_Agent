package com.backend.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_ship_to_snapshots")
public class OrderShipToSnapshot {

    @Id
    @Column(name = "order_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "recipient_name", length = 100, nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20, nullable = false)
    private String recipientPhone;

    @Column(name = "zipcode", length = 20, nullable = false)
    private String zipcode;

    @Column(name = "address1", length = 255, nullable = false)
    private String address1;

    @Column(name = "address2", length = 255)
    private String address2;

    @Builder
    public OrderShipToSnapshot(Order order,
                               String recipientName,
                               String recipientPhone,
                               String zipcode,
                               String address1,
                               String address2) {
        this.order = order;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
    }

    void setOrder(Order order) {
        this.order = order;
    }
}

