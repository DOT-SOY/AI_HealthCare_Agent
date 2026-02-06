package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 루틴 추천 모달에서 "루틴 생성하기" 시 사용하는 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoutinesFromRecommendationRequest {
    /** 시작일 (기본: 오늘) */
    private LocalDate startDate;
    /** 분할 타입 (2, 4, 5) */
    private Integer splitType;
    /** 일차별 데이터: [{ dayIndex, label, exercises: [{ exercise_name, body_part }] }] */
    private List<DayRecommendation> days;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayRecommendation {
        private Integer dayIndex;
        private String label;
        private List<Map<String, Object>> exercises;
    }
}
