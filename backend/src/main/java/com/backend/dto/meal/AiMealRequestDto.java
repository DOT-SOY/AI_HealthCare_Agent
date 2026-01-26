package com.backend.dto.meal;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMealRequestDto {

    // [1] 요청 타입 (필수)
    // - GENERATE: 식단 생성
    // - REPLAN: 재분배
    // - ANALYZE_IMAGE: 음식 사진 분석 (Vision)
    // - ADVICE: 심층 상담
    private String requestType;

    // [2] 사용자 프로필 (식단 생성/재분배 시 필요)
    private UserProfile profile;

    // [3] 목표 상세 (식단 생성/재분배 시 필요)
    private GoalSpec goal;

    // [4] 컨텍스트 데이터 (재분배/상담 시 필요)
    private List<MealDto> currentMeals; // 현재까지의 식단 기록
    private String userQuestion;        // 상담 질문

    // [5] Vision AI 전용 (사진 분석 시에만 채워서 보냄)
    // DB 저장 안 함. AI한테 던져주고 "이거 뭐임?" 물어보는 용도.
    private String foodImageBase64; 


    // =================================================================
    // [Inner Classes] 데이터 구조화 (엔터프라이즈 표준)
    // =================================================================
    @Getter @AllArgsConstructor @Builder
    public static class UserProfile {
        private Long userId;
        private Integer age;
        private String gender;
        private Double height;
        private Double weight;
        private String activityLevel;
        private List<String> allergies;
        private List<String> likedFoods;
        private List<String> dislikedFoods;
    }

    @Getter @AllArgsConstructor @Builder
    public static class GoalSpec {
        private String goalType; // DIET, BULK_UP
        private Integer targetCalories;
        private Integer targetCarbs;
        private Integer targetProtein;
        private Integer targetFat;
        private Integer mealCount; // 하루 3끼? 4끼?
    }
}