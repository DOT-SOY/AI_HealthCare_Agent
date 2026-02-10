package com.backend.service.meal.advice;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public interface MealAdviceService {
    CompletableFuture<Void> asyncDeepAdvice(Long userId, LocalDate date);
}







