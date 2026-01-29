package com.backend.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_buyer_snapshots")
public class OrderBuyerSnapshot {

    @Id
    @Column(name = "order_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "buyer_name", length = 100, nullable = false)
    private String buyerName;

    @Column(name = "buyer_email", length = 255)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 20, nullable = false)
    private String buyerPhone;

    @Builder
    public OrderBuyerSnapshot(Order order,
                              String buyerName,
                              String buyerEmail,
                              String buyerPhone) {
        this.order = order;
        this.buyerName = buyerName;
        this.buyerEmail = buyerEmail;
        this.buyerPhone = buyerPhone;
    }

    void setOrder(Order order) {
        this.order = order;
    }
}

