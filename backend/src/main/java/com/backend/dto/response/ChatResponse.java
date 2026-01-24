package com.backend.dto.response;

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
    private String aiAnswer;
    private boolean requiresDbCheck;
}

