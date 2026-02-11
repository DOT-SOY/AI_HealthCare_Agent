package com.backend.service.meal.replan;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public interface MealReplanService {
    CompletableFuture<Void> asyncMealReplan(Long userId, LocalDate date);

    CompletableFuture<Void> asyncRedistributeAfterMealTimeSkip(Long userId, LocalDate date, String skippedMealTime);
}







