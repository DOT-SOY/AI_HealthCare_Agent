package com.backend.domain.meal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.Instant;

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
    public enum MealTime {
        BREAKFAST("아침"),
        LUNCH("점심"),
        DINNER("저녁"),
        SNACK("간식");

        private final String label;

        MealTime(String label) {
            this.label = label;
        }
    }

    @Getter
    public enum MealStatus {
        PLANNED("계획"),
        EATEN("섭취"),
        SKIPPED("건너뜀");

        private final String description;

        MealStatus(String description) {
            this.description = description;
        }
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

    /**
     * [계획 버전/활성 플래그]
     * - 덮어쓰기(replan/overwrite) 시 기존 PLANNED를 삭제하지 않고 inactive로 전환하여 "직전 계획"을 보존합니다.
     * - UI/집계/겹침검사는 activePlan=true인 PLANNED만 기준으로 삼습니다.
     *
     * 정책:
     * - 끼니(mealTime)별 "직전 1개"만 보관 (더 이전 inactive는 정리)
     */
    @Column(name = "plan_version", nullable = false)
    @Builder.Default
    private Integer planVersion = 1;

    @Column(name = "active_plan", nullable = false)
    @Builder.Default
    private Boolean activePlan = true;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    public void deactivatePlan(Instant replacedAt) {
        this.activePlan = false;
        this.replacedAt = replacedAt;
    }

    /**
     * 계획 교체 로직에서만 사용하는 최소 Setter (컴파일/캡슐화 균형)
     */
    public void setActivePlan(Boolean activePlan) {
        this.activePlan = activePlan;
    }

    public void setPlanVersion(Integer planVersion) {
        this.planVersion = planVersion;
    }

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


