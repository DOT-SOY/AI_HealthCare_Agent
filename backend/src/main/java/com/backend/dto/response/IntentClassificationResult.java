package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentClassificationResult {
    private String intent; // PAIN_REPORT, GENERAL_CHAT
    private Map<String, Object> entities; // body_part, intensity 등
    private String aiAnswer;
    private boolean requiresDbCheck;
}
