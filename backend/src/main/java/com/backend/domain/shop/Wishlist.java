package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "wishlists",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_wishlist_member_product",
            columnNames = {"member_id", "product_id"}
        )
    }
)
public class Wishlist extends BaseEntity {

    // 복합 키
    @EmbeddedId
    private WishlistId id;

    // 회원
    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", referencedColumnName = "member_id", nullable = false)
    private Member member;

    // 상품
    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Builder
    public Wishlist(Member member, Product product) {
        this.member = member;
        this.product = product;
        this.id = new WishlistId(member.getId(), product.getId());
    }
}
