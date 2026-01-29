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
    // [Inner Enum Definition]
    // =================================================================
    @Getter
    @AllArgsConstructor
    public enum MealTime {
        BREAKFAST("아침"),
        LUNCH("점심"),
        DINNER("저녁"),
        SNACK("간식");

        private final String label;

        public String getLabel() {
            return label;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum MealStatus {
        PLANNED("계획"),
        EATEN("섭취"),
        SKIPPED("건너뜀");

        private final String description;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false, length = 20)
    private MealTime mealTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MealStatus status = MealStatus.PLANNED;

    @Column(name = "is_additional", nullable = false)
    @Builder.Default
    private Boolean isAdditional = false;

    // --- 섭취 정보 (Current) ---
    @Column(name = "food_name")
    private String foodName;

    @Column(name = "serving_size")
    private String servingSize;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "carbs")
    private Integer carbs;

    @Column(name = "protein")
    private Integer protein;

    @Column(name = "fat")
    private Integer fat;

    // --- AI 제안 정보 (Original) - 분석 UI용 ---
    @Column(name = "original_food_name")
    private String originalFoodName;

    @Column(name = "original_serving_size")
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
     * 식단 정보 업데이트 (Original 정보는 보존)
     */
    public void updateMealInfo(String foodName, String servingSize,
                               Integer calories, Integer carbs, Integer protein, Integer fat,
                               MealStatus status) {
        this.foodName = foodName;
        this.servingSize = servingSize;
        this.calories = calories;
        this.carbs = carbs;
        this.protein = protein;
        this.fat = fat;
        this.status = status;
    }

    /**
     * 상태 변경
     */
    public void changeStatus(MealStatus status) {
        this.status = status;
    }
}


