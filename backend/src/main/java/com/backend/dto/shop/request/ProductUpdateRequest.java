package com.backend.dto.shop.request;

import com.backend.domain.shop.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 수정 요청 DTO
 * API 설계 원칙에 따라 PATCH 메서드에 맞게 부분 업데이트 지원 (null이 아닌 필드만 업데이트)
 */
@Getter
@Setter
public class ProductUpdateRequest {
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal basePrice;

    // 판매 상태 (DRAFT, ACTIVE, INACTIVE)
    // null이면 기존 상태 유지
    private ProductStatus status;

    // 이미지 파일 경로 목록 (별도 업로드된 파일의 filePath)
    // null이면 기존 이미지 유지, 빈 리스트면 모든 이미지 제거, 값이 있으면 해당 이미지들로 교체
    private List<String> imageFilePaths;

    // 상품 변형(Variant) 목록
    // null이면 기존 variants 유지, 빈 리스트면 모든 variants 제거, 값이 있으면 해당 variants로 교체
    @Valid
    private List<ProductVariantRequest> variants;

    // 카테고리 ID 목록 (다중 카테고리 지원)
    // null이면 기존 categories 유지, 빈 리스트면 모든 categories 제거, 값이 있으면 해당 categories로 교체
    private List<Long> categoryIds;

    // 카테고리 타입(Enum) 목록 — FOOD, SUPPLEMENT 등. categoryIds 대신 사용 가능
    private List<String> categoryTypes;
}
