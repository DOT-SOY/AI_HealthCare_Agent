package com.backend.dto.meal;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealDashboardDto {

    private String date;

    private MealTargetDto dayTarget; // 목표 정보
    private List<MealDto> meals;     // 식단 리스트
    private String aiAnalysis;       // AI 분석

    // [그래프용 영양소 정보]
    private NutritionSummary calories;
    private NutritionSummary carbs;
    private NutritionSummary protein;
    private NutritionSummary fat;

    // [끼니별 섹션]
    private MealTimeSection breakfast;
    private MealTimeSection lunch;
    private MealTimeSection dinner;
    private MealTimeSection snack;

    // --- [탭 1: 식단 변동 내역] ---
    private List<String> analysisComments;

    // --- Inner Classes ---
    @Getter @AllArgsConstructor @Builder
    public static class NutritionSummary {
        private Integer goal;
        private Integer current;
        private Integer percent;
        private String status; 
    }

    @Getter @AllArgsConstructor @Builder
    public static class MealTimeSection {
        /**
         * 해당 끼니가 "끼니 전체 생략" 상태인지 (UI에서 취소/재정비 흐름에 사용)
         * - meals 리스트에서는 SKIPPED 항목이 숨겨질 수 있으므로 별도 플래그로 제공합니다.
         */
        private Boolean skipped;
        private Integer totalCalories;
        private Integer totalCarbs;
        private Integer totalProtein;
        private Integer totalFat;
        private Integer percentCarbs;   
        private Integer percentProtein;
        private Integer percentFat;
        private List<MealDto> meals;
    }
}


