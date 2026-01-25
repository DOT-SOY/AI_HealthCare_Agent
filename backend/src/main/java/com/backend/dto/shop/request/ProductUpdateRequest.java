package com.backend.dto.shop.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductUpdateRequest {
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "가격은 0 이상이어야 합니다")
    private BigDecimal basePrice;

    // 이미지 파일 경로 목록 (별도 업로드된 파일의 filePath)
    // null이면 기존 이미지 유지, 빈 리스트면 모든 이미지 제거, 값이 있으면 해당 이미지들로 교체
    private List<String> imageFilePaths;

    // TODO: 추후 ProductStatus enum 확장 시 사용
    // private ProductStatus status;
}
