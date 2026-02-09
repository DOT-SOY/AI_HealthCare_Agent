package com.backend.domain.shop;

import com.backend.domain.AuditEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "product_review_images",
        indexes = {
                @Index(name = "idx_product_review_images_review", columnList = "review_id")
        }
)
public class ProductReviewImage extends AuditEntity {

    // PK (UUID)
    @Id
    @JdbcTypeCode(SqlTypes.CHAR) // UUID를 CHAR(36)로 저장 (DB 호환성 우선)
    @Column(name = "uuid", nullable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview review;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Builder
    public ProductReviewImage(ProductReview review, String filePath) {
        this.uuid = UUID.randomUUID();
        this.review = review;
        this.filePath = filePath != null ? filePath.trim() : null;
    }
}
