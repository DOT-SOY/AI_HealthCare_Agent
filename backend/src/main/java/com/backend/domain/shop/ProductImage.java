package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
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
@Table(name = "product_images")
public class ProductImage extends BaseEntity {

    // PK (UUID)
    @Id
    @JdbcTypeCode(SqlTypes.CHAR) // UUID를 CHAR(36)로 저장 (DB 호환성 우선)
    @Column(name = "uuid", nullable = false, length = 36)
    private UUID uuid;

    // 상품
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 이미지 URL
    @Column(nullable = false, length = 1000)
    private String url;

    // 대표 이미지 여부
    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;

    @Builder
    public ProductImage(Product product,
                      String url,
                      Boolean primaryImage) {
        this.uuid = UUID.randomUUID();
        this.product = product;
        this.url = url;
        this.primaryImage = (primaryImage != null) ? primaryImage : false;
    }

    // 대표 이미지로 설정
    public void markAsPrimary() {
        this.primaryImage = true;
    }

    // 일반 이미지로 설정
    public void markAsSecondary() {
        this.primaryImage = false;
    }

    // 이미지 URL 변경
    public void changeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("이미지 URL은 필수입니다.");
        }
        this.url = url.trim();
    }
}
