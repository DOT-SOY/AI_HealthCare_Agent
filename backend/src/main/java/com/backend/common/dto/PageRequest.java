package com.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

@Getter
@Setter
public class PageRequest {
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    private int page = 1;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    @JsonProperty("page_size")  // api-design-principles 문서에 맞춰 snake_case 지원
    private int pageSize = 20;

    private String sortBy = "createdAt";
    private Sort.Direction direction = Sort.Direction.DESC;

    public org.springframework.data.domain.PageRequest toPageable() {
        return org.springframework.data.domain.PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(direction, sortBy)
        );
    }
}
