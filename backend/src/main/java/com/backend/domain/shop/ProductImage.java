package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_images")
@Where(clause = "deleted_at IS NULL")
@SQLDelete(sql = "UPDATE product_images SET deleted_at = NOW() WHERE uuid = ?")
public class ProductImage extends BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", nullable = false, length = 36)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;

    @Builder
    public ProductImage(Product product,
                      String filePath,
                      Boolean primaryImage) {
        this.uuid = UUID.randomUUID();
        this.product = product;
        this.filePath = filePath;
        this.primaryImage = (primaryImage != null) ? primaryImage : false;
    }

    public void markAsPrimary() {
        this.primaryImage = true;
    }

    // 일반 이미지로 설정
    public void markAsSecondary() {
        this.primaryImage = false;
    }

    public void changeFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("파일 경로는 필수입니다.");
        }
        this.filePath = filePath.trim();
    }
}
