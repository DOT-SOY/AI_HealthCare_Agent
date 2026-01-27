package com.backend.domain.meal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "meal_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Meal {

    // =================================================================
    // [Inner Enum Definition] 파일 증가 없이 내부에서 관리
    // =================================================================
    @Getter
    @AllArgsConstructor
    public enum MealStatus {
        PLANNED("계획됨"),
        EATEN("섭취 완료"),
        SKIPPED("건너뜀");

        private final String description;
    }

    @Getter
    @AllArgsConstructor
    public enum MealTime {
        BREAKFAST("아침", 1),
        LUNCH("점심", 2),
        DINNER("저녁", 3),
        SNACK("간식", 4);

        private final String label;
        private final int order; // 정렬 순서 보장용
    }
    // =================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    // [Enum 적용] 순서 보장 및 오타 방지
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false, length = 20)
    private MealTime mealTime;

    // [Enum 적용] 상태 관리
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MealStatus status;

    @Column(name = "is_additional", nullable = false)
    private Boolean isAdditional;

    // --- Current (실제 섭취 정보) ---
    @Column(name = "food_name", length = 100)
    private String foodName;

    @Column(name = "serving_size", length = 50)
    private String servingSize;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "carbs")
    private Integer carbs;

    @Column(name = "protein")
    private Integer protein;

    @Column(name = "fat")
    private Integer fat;

    // --- Original (AI 제안 정보) ---
    @Column(name = "original_food_name", length = 100)
    private String originalFoodName;

    @Column(name = "original_serving_size", length = 50)
    private String originalServingSize;

    @Column(name = "original_calories")
    private Integer originalCalories;

    @Column(name = "original_carbs")
    private Integer originalCarbs;

    @Column(name = "original_protein")
    private Integer originalProtein;

    @Column(name = "original_fat")
    private Integer originalFat;

    /**
     * 식단 정보 업데이트
     */
    public void updateMealInfo(String foodName, String servingSize, Integer calories, 
                               Integer carbs, Integer protein, Integer fat, MealStatus status) {
        this.foodName = foodName;
        this.servingSize = servingSize;
        this.calories = calories;
        this.carbs = carbs;
        this.protein = protein;
        this.fat = fat;
        this.status = status;
    }

    public void changeStatus(MealStatus status) {
        this.status = status;
    }

    /**
     * 계획(Original) 대비 수정된 식단임을 표시한다.
     * - isAdditional=true 로 두면 프런트 "상세분석"에서 변경 기록을 구분할 수 있다.
     */
    public void markAsChangedFromPlan() {
        this.isAdditional = true;
    }
}