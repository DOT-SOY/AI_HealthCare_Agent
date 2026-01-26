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

    // --- [탭 2: AI 식단 분석] ---
    private String aiAnalysis; 


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