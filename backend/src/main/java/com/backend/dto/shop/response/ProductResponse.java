package com.backend.dto.shop.response;

import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private ProductStatus status;
    private BigDecimal basePrice;
    private Instant createdAt;
    private Instant updatedAt;
    private Long createdBy; // 작성자 ID
    private List<ProductImageResponse> images;
    private List<ProductVariantResponse> variants;
    private List<CategoryResponse> categories;
    private ReviewSummaryResponse reviewSummary;
    /** 로그인 회원이 해당 상품을 구매했고 아직 리뷰를 쓰지 않았을 때만 true (미로그인/비구매/이미 작성 시 false 또는 null) */
    private Boolean canReview;
    
    /**
     * Product 엔티티로부터 ProductResponse를 생성하는 정적 팩토리 메서드
     * 
     * @param product Product 엔티티
     * @return ProductResponse (images는 null로 설정됨, 서비스 레이어에서 별도 처리)
     */
    public static ProductResponse from(Product product) {
        if (product == null) {
            return null;
        }
        
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .status(product.getStatus())
                .basePrice(product.getBasePrice())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(product.getCreatedBy() != null ? product.getCreatedBy().getId() : null)
                .images(null) // images는 서비스 레이어에서 별도 처리
                .build();
    }
}
