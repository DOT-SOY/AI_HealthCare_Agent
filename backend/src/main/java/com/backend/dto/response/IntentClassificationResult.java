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
    private String intent; // WORKOUT, WORKOUT_REVIEW, PAIN_REPORT, GENERAL_CHAT 등
    private String action; // QUERY, RECOMMEND, MODIFY, REPORT, CHAT 등
    private Map<String, Object> entities; // date, exercise_name, body_part, intensity, split_type 등
    private String aiAnswer;
    private boolean requiresDbCheck;
    /** 원문 사용자 입력 (분할 파싱 폴백 등에 사용) */
    private String userInput;
}
