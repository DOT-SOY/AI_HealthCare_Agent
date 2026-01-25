package com.backend.dto.shop.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

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

    // 이미지 파일 경로 목록 (별도 업로드된 파일의 filePath)
    private List<String> imageFilePaths;

    // TODO: 추후 member 도메인 추가 시 admin 권한 체크
    // @NotNull(message = "작성자 ID는 필수입니다")
    // private Long createdBy;
}
