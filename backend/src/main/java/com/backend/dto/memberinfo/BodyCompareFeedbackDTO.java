package com.backend.dto.memberinfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 저장 후 직전 기록과 비교하여 반환하는 피드백 DTO.
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BodyChangeItem {
        private String message;
        private String change;
    }
}
