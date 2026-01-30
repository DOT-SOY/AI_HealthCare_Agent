package com.backend.dto.shop.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReplyResponse {

    private Long id;
    private Long reviewId;
    private String content;
    @JsonProperty("author_display_name")
    private String authorDisplayName;
    @JsonProperty("created_at")
    private Instant createdAt;
}
