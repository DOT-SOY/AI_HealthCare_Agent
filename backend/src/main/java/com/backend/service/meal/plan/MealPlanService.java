package com.backend.service.meal.plan;

import com.backend.dto.meal.MealDto;

import java.time.LocalDate;
import java.util.List;

public interface MealPlanService {
    void updatePlannedMeals(Long userId, LocalDate date, List<MealDto> newPlans);

    /**
     * [덮어쓰기 전용]
     * - 기존 식단 유무/추가 여부와 관계없이, 해당 날짜의 "미완료(status != EATEN)" 항목은 전부 삭제 후 새 계획으로 교체합니다.
     * - 완료(EATEN)는 그대로 보존합니다.
     */
    void overwritePlannedMealsKeepEaten(Long userId, LocalDate date, List<MealDto> newPlans);
}


