package com.backend.dto.shop.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCreateRequest {

    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다")
    private Integer rating;

    @Size(max = 2000, message = "리뷰 내용은 2000자 이하여야 합니다")
    private String content;
}
