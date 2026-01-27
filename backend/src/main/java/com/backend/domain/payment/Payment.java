package com.backend.domain.payment;

import com.backend.domain.BaseEntity;
import com.backend.domain.order.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_payment_key", columnNames = "payment_key")
        },
        indexes = {
                @Index(name = "idx_payments_order_id", columnList = "order_id")
        }
)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_key", length = 100, nullable = false)
    private String paymentKey;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Lob
    @Column(name = "raw_response")
    private String rawResponse;

    @Builder
    public Payment(Order order,
                   PaymentProvider provider,
                   PaymentStatus status,
                   String paymentKey,
                   Instant approvedAt,
                   String rawResponse) {
        this.order = order;
        this.provider = provider;
        this.status = status;
        this.paymentKey = paymentKey;
        this.approvedAt = approvedAt;
        this.rawResponse = rawResponse;
    }
}

