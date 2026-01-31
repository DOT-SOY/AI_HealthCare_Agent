package com.backend.dto.memberinfo;

import lombok.*;

import java.util.List;

/**
 * 인바디 직전 1 row와 비교한 규칙 기반 피드백 응답
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BodyCompareFeedbackDTO {

    private String summary;
    private List<BodyChangeItem> bodyChanges;
    private String mealFeedback;
    private String exerciseFeedback;
    private List<String> recommendations;
    private boolean hasComparison;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BodyChangeItem {
        private String type;
        private String change;
        private String value;
        private String message;
    }
}
