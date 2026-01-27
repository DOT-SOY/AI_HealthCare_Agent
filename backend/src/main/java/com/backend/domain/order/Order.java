package com.backend.domain.order;

import com.backend.domain.BaseEntity;
import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_order_no", columnNames = "order_no")
        },
        indexes = {
                @Index(name = "idx_orders_member_id", columnList = "member_id")
        }
)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", length = 50, nullable = false)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", referencedColumnName = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private OrderStatus status;

    // 게스트 주문용 정보
    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    // BCrypt 해시 저장
    @Column(name = "guest_password_hash", length = 100)
    private String guestPasswordHash;

    @Column(name = "total_item_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalItemAmount;

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee;

    @Column(name = "total_payable_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalPayableAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // TODO: 주문 상세 조회 시 Order -> OrderItem 로딩을 위한 EntityGraph 후보
    private final List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    // TODO: 주문 상세 조회 시 Buyer Snapshot 함께 로딩용 EntityGraph 후보
    private OrderBuyerSnapshot buyerSnapshot;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    // TODO: 주문 상세 조회 시 ShipTo Snapshot 함께 로딩용 EntityGraph 후보
    private OrderShipToSnapshot shipToSnapshot;

    @Builder
    public Order(String orderNo,
                 Member member,
                 OrderStatus status,
                 String guestPhone,
                 String guestPasswordHash,
                 BigDecimal totalItemAmount,
                 BigDecimal shippingFee,
                 BigDecimal totalPayableAmount) {
        this.orderNo = orderNo;
        this.member = member;
        this.status = status;
        this.guestPhone = guestPhone;
        this.guestPasswordHash = guestPasswordHash;
        this.totalItemAmount = totalItemAmount;
        this.shippingFee = shippingFee;
        this.totalPayableAmount = totalPayableAmount;
    }

    public void markPaid(Instant paidAt) {
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    public void setBuyerSnapshot(OrderBuyerSnapshot snapshot) {
        this.buyerSnapshot = snapshot;
        snapshot.setOrder(this);
    }

    public void setShipToSnapshot(OrderShipToSnapshot snapshot) {
        this.shipToSnapshot = snapshot;
        snapshot.setOrder(this);
    }
}

