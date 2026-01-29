package com.backend.domain.order;

import com.backend.domain.AuditEntity;
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
                @Index(name = "idx_orders_member_id", columnList = "member_id"),
                @Index(name = "idx_orders_member_created", columnList = "member_id, created_at")
        }
)
public class Order extends AuditEntity {

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

    /** 총 결제 금액 (상품 합계 + 배송비) */
    @Column(name = "total_payable_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalPayableAmount;

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * 결제 이후 후처리(재고 차감 + 장바구니 정리 등)가 완료되었는지 여부.
     * - false: 아직 finalizeAfterPaid 미실행 또는 실패
     * - true : finalizeAfterPaid 정상 완료 (멱등 보장용)
     */
    @Column(name = "finalized", nullable = false)
    private boolean finalized = false;

    /**
     * finalizeAfterPaid 수행 중임을 나타내는 플래그.
     * 동시 요청 간 "승자" 선점을 위해 사용된다.
     */
    @Column(name = "finalizing", nullable = false)
    private boolean finalizing = false;

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
                 BigDecimal totalPayableAmount,
                 BigDecimal shippingFee) {
        this.orderNo = orderNo;
        this.member = member;
        this.status = status;
        this.guestPhone = guestPhone;
        this.guestPasswordHash = guestPasswordHash;
        this.totalPayableAmount = totalPayableAmount;
        this.shippingFee = shippingFee;
    }

    /** 상품 합계 (totalPayableAmount - shippingFee). 저장 필드 없음. */
    public BigDecimal getTotalItemAmount() {
        return totalPayableAmount.subtract(shippingFee);
    }

    /** pay/ready 성공 시 CREATED → PAYMENT_PENDING */
    public void toPaymentPending() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("CREATED 상태에서만 PAYMENT_PENDING으로 전이 가능합니다.");
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    /**
     * 결제 완료 전이 메서드.
     * - 허용 전이: CREATED → PAID, PAYMENT_PENDING → PAID
     * - 그 외 상태에서 호출 시 IllegalStateException
     */
    public void markPaid(Instant paidAt) {
        if (this.status != OrderStatus.CREATED && this.status != OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException("CREATED 또는 PAYMENT_PENDING 상태에서만 PAID로 전이 가능합니다.");
        }
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
    }

    /**
     * finalizeAfterPaid 정상 완료 후 마킹.
     * - finalized = true
     * - finalizing = false
     */
    public void markFinalized() {
        this.finalized = true;
        this.finalizing = false;
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

