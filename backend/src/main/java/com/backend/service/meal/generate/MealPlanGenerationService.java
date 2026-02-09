package com.backend.service.meal.generate;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public interface MealPlanGenerationService {
    CompletableFuture<Integer> asyncGeneratePlanFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType);

    CompletableFuture<Integer> asyncGeneratePlanFillMissingFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType);
}







