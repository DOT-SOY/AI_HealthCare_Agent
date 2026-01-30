package com.backend.dto.shop.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    @JsonProperty("product_id")
    private Long productId;
    @JsonProperty("member_id")
    private Long memberId;
    @JsonProperty("display_name")
    private String displayName;
    private int rating;
    private String content;
    @JsonProperty("created_at")
    private Instant createdAt;
    @JsonProperty("updated_at")
    private Instant updatedAt;
    private List<ReplyResponse> replies;
}
