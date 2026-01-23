package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "products")
public class Product extends BaseEntity {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 상품명
    @Column(nullable = false, length = 200)
    private String name;

    // 상품 설명
    @Lob
    @Column(nullable = false)
    private String description;

    // 판매 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    // 기본 가격 - 초기엔 0원
    @Column(name = "base_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    // 작성자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    // 관계 정리
    // FetchType.LAZY 명시로 N+1 문제 방지 (필요할 때만 조회)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20) // N+1 문제 방지: 20개씩 배치로 조회
    private final List<ProductImage> images = new ArrayList<>();
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 20) // N+1 문제 방지: 20개씩 배치로 조회
    private final List<ProductVariant> variants = new ArrayList<>();

    @Builder
    public Product(String name,
                   String description,
                   ProductStatus status,
                   BigDecimal basePrice,
                   Member createdBy) {
        this.name = name;
        this.description = description;
        this.status = (status != null) ? status : ProductStatus.DRAFT;
        this.basePrice = (basePrice != null) ? basePrice : BigDecimal.ZERO;
        this.createdBy = createdBy;
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
    }

    public void changeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        this.name = name.trim();
    }

    public void changeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("상품 설명은 필수입니다.");
        }
        this.description = description.trim();
    }

    public void changeBasePrice(BigDecimal basePrice) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        this.basePrice = basePrice;
    }

    public BigDecimal resolvePrice(BigDecimal variantPrice) {
        return (variantPrice != null) ? variantPrice : this.basePrice;
    }
}
