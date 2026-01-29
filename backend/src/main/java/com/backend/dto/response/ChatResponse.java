package com.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String intent;
    private Map<String, Object> entities;
    
    @JsonProperty("ai_answer") // Python AI 서버가 snake_case로 반환하므로 매핑 필요
    private String aiAnswer;
    
    @JsonProperty("requires_db_check") // Python AI 서버가 snake_case로 반환하므로 매핑 필요
    private boolean requiresDbCheck;
}

