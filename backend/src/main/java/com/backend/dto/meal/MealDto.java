package com.backend.dto.meal;

import com.backend.domain.meal.Meal;
import lombok.*;

import java.time.LocalDate;

/**
 * [식단 기본 DTO]
 * 식단을 생성(Create)하거나 수정(Update)할 때 사용되는 데이터 객체입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealDto {

    private Long scheduleId;
    private Long userId;
    private LocalDate mealDate;
    private String mealTime;      // "BREAKFAST" (String)
    private String status;        // "PLANNED" (String)
    private Boolean isAdditional;

    // --- 섭취 정보 (Current) ---
    private String foodName;
    private String servingSize;
    private Integer calories;
    private Integer carbs;
    private Integer protein;
    private Integer fat;

    // --- AI 제안 정보 (Original) - [필수] 분석 UI용 ---
    private String originalFoodName;
    private String originalServingSize;
    private Integer originalCalories;
    private Integer originalCarbs;
    private Integer originalProtein;
    private Integer originalFat;

    /**
     * DTO -> Entity 변환 (저장용)
     * String으로 들어온 Enum 값을 실제 Enum 타입으로 변환하여 매핑합니다.
     */
    public Meal toEntity(Long userId) {
        return Meal.builder()
                .scheduleId(this.scheduleId)
                .userId(userId)
                .mealDate(this.mealDate)
                // String -> Enum 변환
                .mealTime(Meal.MealTime.valueOf(this.mealTime)) 
                .status(this.status == null ? Meal.MealStatus.PLANNED : Meal.MealStatus.valueOf(this.status))
                .isAdditional(this.isAdditional != null && this.isAdditional)
                
                .foodName(this.foodName)
                .servingSize(this.servingSize)
                .calories(this.calories)
                .carbs(this.carbs)
                .protein(this.protein)
                .fat(this.fat)
                
                // 생성 시점엔 Original 정보도 같이 세팅 (AI가 준 값 그대로)
                .originalFoodName(this.originalFoodName)
                .originalServingSize(this.originalServingSize)
                .originalCalories(this.originalCalories)
                .originalCarbs(this.originalCarbs)
                .originalProtein(this.originalProtein)
                .originalFat(this.originalFat)
                .build();
    }

    /**
     * Entity -> DTO 변환 (조회용)
     */
    public static MealDto fromEntity(Meal meal) {
        if (meal == null) {
            return null;
        }
        return MealDto.builder()
                .scheduleId(meal.getScheduleId())
                .userId(meal.getUserId())
                .mealDate(meal.getMealDate())
                .mealTime(meal.getMealTime() != null ? meal.getMealTime().name() : null)
                .status(meal.getStatus() != null ? meal.getStatus().name() : null)
                .isAdditional(meal.getIsAdditional())
                
                .foodName(meal.getFoodName())
                .servingSize(meal.getServingSize())
                .calories(meal.getCalories())
                .carbs(meal.getCarbs())
                .protein(meal.getProtein())
                .fat(meal.getFat())
                
                .originalFoodName(meal.getOriginalFoodName())
                .originalServingSize(meal.getOriginalServingSize())
                .originalCalories(meal.getOriginalCalories())
                .originalCarbs(meal.getOriginalCarbs())
                .originalProtein(meal.getOriginalProtein())
                .originalFat(meal.getOriginalFat())
                .build();
    }
}