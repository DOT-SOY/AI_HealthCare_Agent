package com.backend.service.meal.vision;

import com.backend.dto.meal.AiMealVisionFollowupDto;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MealVisionService {
    CompletableFuture<Void> asyncVisionAnalysis(Long userId, String base64Image);

    CompletableFuture<AiMealVisionFollowupDto.Response> visionFollowup(Long userId, AiMealVisionFollowupDto.Request request);

    String applyVisionAdd(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood);

    String applyVisionReplace(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood);
}







