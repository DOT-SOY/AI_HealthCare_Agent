package com.backend.service.meal.intake;

import com.backend.dto.meal.MealDto;

import java.time.LocalDate;

public interface MealIntakeService {
    MealDto registerAdditionalMeal(Long userId, MealDto mealDto);

    MealDto updateMeal(Long scheduleId, MealDto mealDto);

    void toggleMealStatus(Long scheduleId, String status);

    void removeOrSkipMeal(Long scheduleId, boolean isPermanentDelete);

    String toggleMealTimeComplete(Long userId, LocalDate date, String mealTime);

    String toggleMealTimeSkip(Long userId, LocalDate date, String mealTime);

    String toggleItemByFoodName(Long userId, LocalDate date, String mealTimeOrNull, String foodName, String mode);
}







