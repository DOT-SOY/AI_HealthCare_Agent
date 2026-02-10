package com.backend.service.meal.advice;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.MealDto;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.target.MealTargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealAdviceServiceImpl implements MealAdviceService {

    private final MealSearch mealSearch;
    private final AiMealClient aiMealClient;
    private final MealTargetService mealTargetService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncDeepAdvice(Long userId, LocalDate date) {
        log.info("[Async] AI 심층 상담 요청 - User: {}, Date: {}", userId, date);

        List<Meal> currentMeals = mealSearch.findMealsByDateAndUser(userId, date);
        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("ADVICE")
                .currentMeals(currentMeals.stream().map(MealDto::fromEntity).toList())
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    mealTargetService.updateAiFeedback(userId, date, response.getAdviceComment());
                    messagingTemplate.convertAndSend("/topic/meal/advice/" + userId, response.getAdviceComment());
                    log.info("[Async] 심층 상담 완료 및 DB 저장 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] 심층 상담 실패: ", throwable);
                    return null;
                });
    }
}







