package com.backend.domain.cart;

import com.backend.domain.BaseEntity;
import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "carts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_cart_guest_token",
            columnNames = {"guest_token"}
        )
    }
)
public class Cart extends BaseEntity {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", referencedColumnName = "member_id")
    private Member member;

    // 게스트 토큰 (nullable, unique)
    @Column(name = "guest_token", length = 100)
    private String guestToken;

    // 장바구니 아이템 목록
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20) // N+1 문제 방지: 20개씩 배치로 조회
    private final List<CartItem> items = new ArrayList<>();

    @Builder
    public Cart(Member member, String guestToken) {
        // member_id와 guest_token 중 하나만 있어야 함
        if (member != null && guestToken != null) {
            throw new IllegalArgumentException("회원 장바구니와 게스트 장바구니는 동시에 설정할 수 없습니다.");
        }
        if (member == null && guestToken == null) {
            throw new IllegalArgumentException("회원 ID 또는 게스트 토큰 중 하나는 필수입니다.");
        }
        this.member = member;
        this.guestToken = guestToken;
    }

    // 장바구니 아이템 추가
    public void addItem(CartItem item) {
        this.items.add(item);
        item.setCart(this);
    }

    // 장바구니 아이템 제거
    public void removeItem(CartItem item) {
        this.items.remove(item);
        item.setCart(null);
    }
}
