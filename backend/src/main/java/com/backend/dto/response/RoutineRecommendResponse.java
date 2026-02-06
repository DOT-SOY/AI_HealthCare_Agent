package com.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 /routine/recommend 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutineRecommendResponse {
    private String message;
    private List<Map<String, Object>> exercises;

    @JsonProperty("alternatives")
    private Map<String, Object> alternatives;

    @JsonProperty("split_definitions")
    private Map<String, Object> splitDefinitions;
}
