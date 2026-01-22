package com.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {
    private List<T> items;  // api-design-principles 문서에 맞춰 items로 변경
    private int page;
    @JsonProperty("page_size")  // api-design-principles 문서에 맞춰 snake_case 지원
    private int pageSize;
    private long total;  // api-design-principles 문서에 맞춰 total로 변경
    private int pages;  // api-design-principles 문서에 맞춰 pages로 변경
    @JsonProperty("has_next")
    private boolean hasNext;
    @JsonProperty("has_previous")
    private boolean hasPrevious;

    public static <T> PageResponse<T> of(Page<T> page, int currentPage) {
        return PageResponse.<T>builder()
                .items(page.getContent())
                .page(currentPage)
                .pageSize(page.getSize())
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
