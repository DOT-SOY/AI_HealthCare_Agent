package com.backend.dto.shop.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReviewCreateRequest {

    @NotNull(message = "평점은 필수입니다")
    @Min(value = 1, message = "평점은 1~5 사이여야 합니다")
    @Max(value = 5, message = "평점은 1~5 사이여야 합니다")
    private Integer rating;

    @Size(max = 2000, message = "리뷰 내용은 2000자 이하여야 합니다")
    private String content;

    /**
     * 업로드된 리뷰 이미지 파일 경로 목록
     */
    @Size(max = 10, message = "리뷰 이미지는 최대 10개까지 업로드할 수 있습니다")
    private List<@Size(max = 500, message = "파일 경로는 500자 이하여야 합니다") String> imageFilePaths;
}
