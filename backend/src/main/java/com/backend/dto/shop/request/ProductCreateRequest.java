package com.backend.dto.shop.request;

import com.backend.domain.shop.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 생성 요청 DTO
 * API 설계 원칙에 따라 명확한 필드명과 검증 어노테이션 사용
 */
@Getter
@Setter
public class ProductCreateRequest {
    @NotBlank(message = "상품명은 필수입니다")
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @NotBlank(message = "상품 설명은 필수입니다")
    private String description;

    @NotNull(message = "기본 가격은 필수입니다")
    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal basePrice;

    // 판매 상태 (DRAFT, ACTIVE, INACTIVE)
    // null이면 기본값 DRAFT로 설정됨
    private ProductStatus status;

    // 이미지 파일 경로 목록 (별도 업로드된 파일의 filePath)
    private List<String> imageFilePaths;

    // 상품 변형(Variant) 목록
    @Valid
    private List<ProductVariantRequest> variants;

    // 카테고리 ID 목록 (다중 카테고리 지원)
    private List<Long> categoryIds;

    // 카테고리 타입(Enum) 목록 — FOOD, SUPPLEMENT 등. categoryIds 대신 사용 가능
    private List<String> categoryTypes;

    // TODO: 추후 member 도메인 추가 시 admin 권한 체크
    // @NotNull(message = "작성자 ID는 필수입니다")
    // private Long createdBy;
}
