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
        name = "product_review_replies",
        indexes = @Index(name = "idx_product_review_replies_review_id", columnList = "review_id")
)
public class ProductReviewReply extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 2000)
    private String content;

    @Builder
    public ProductReviewReply(ProductReview review, Member member, String content) {
        this.review = review;
        this.member = member;
        this.content = (content != null) ? content.trim() : null;
    }
}
