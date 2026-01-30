package com.backend.domain.shop;

import com.backend.domain.AuditEntity;
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
        name = "product_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_reviews_product_member", columnNames = {"product_id", "member_id"}),
        indexes = @Index(name = "idx_product_reviews_product_created", columnList = "product_id, created_at")
)
public class ProductReview extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String content;

    @Builder
    public ProductReview(Product product, Member member, int rating, String content) {
        this.product = product;
        this.member = member;
        this.rating = rating;
        this.content = (content != null) ? content.trim() : null;
    }

    public void update(int rating, String content) {
        this.rating = rating;
        this.content = (content != null) ? content.trim() : null;
    }
}
