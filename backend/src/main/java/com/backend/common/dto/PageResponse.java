package com.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int page;
    @JsonProperty("page_size")
    private int pageSize;
    private long total;
    private int pages;
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
