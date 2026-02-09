package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.*;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.advice.MealAdviceService;
import com.backend.service.meal.dashboard.MealDashboardService;
import com.backend.service.meal.generate.MealPlanGenerationService;
import com.backend.service.meal.intake.MealIntakeService;
import com.backend.service.meal.plan.MealPlanService;
import com.backend.service.meal.replan.MealReplanService;
import com.backend.service.meal.vision.MealVisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * [식단 관리 및 분석 시스템 심장부]
 * - 자바 기반의 정밀 식단 변동 분석 엔진 탑재
 * - Redis/WebSocket 기반의 실시간 비동기 AI 분석 연동
 * - 엔터프라이즈급 트랜잭션 및 예외 처리 로직 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealServiceImpl implements MealService {

    private final MealSearch mealSearch;
    private final MealDashboardService mealDashboardService;
    private final MealIntakeService mealIntakeService;
    private final MealVisionService mealVisionService;
    private final MealReplanService mealReplanService;
    private final MealAdviceService mealAdviceService;
    private final MealPlanGenerationService mealPlanGenerationService;
    private final MealPlanService mealPlanService;

    /**
     * [대시보드 통합 조립]
     * 목표(Target) + 식단(Meal) + 변동 분석(Analysis) 데이터를 하나의 세트로 조립합니다.
     */
    @Override
    public MealDashboardDto getMealDashboard(Long userId, LocalDate date) {
        return mealDashboardService.getMealDashboard(userId, date);
    }

    /**
     * 날짜와 식사 시간으로 식단 조회
     */
    @Override
    public List<MealDto> getMealsByDateAndTime(Long userId, LocalDate date, Meal.MealTime mealTime) {
        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date);
        if (mealTime != null) {
            meals = meals.stream()
                    .filter(m -> m.getMealTime() == mealTime)
                    .collect(Collectors.toList());
        }
        return meals.stream()
                .map(MealDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MealDto registerAdditionalMeal(Long userId, MealDto mealDto) {
        return mealIntakeService.registerAdditionalMeal(userId, mealDto);
    }

    @Override
    @Transactional
    public MealDto updateMeal(Long scheduleId, MealDto mealDto) {
        return mealIntakeService.updateMeal(scheduleId, mealDto);
    }

    @Override
    @Transactional
    public void toggleMealStatus(Long scheduleId, String status) {
        mealIntakeService.toggleMealStatus(scheduleId, status);
    }

    @Override
    @Transactional
    public void removeOrSkipMeal(Long scheduleId, boolean isPermanentDelete) {
        mealIntakeService.removeOrSkipMeal(scheduleId, isPermanentDelete);
    }

    // =================================================================
    // 비동기 AI 엔진 및 WebSocket 통신부
    // =================================================================

    /**
     * [비동기 Vision AI 분석]
     * 
     * 변경 사항:
     * - @Transactional 제거: 비동기 메서드에서는 별도 트랜잭션 필요 없음
     * - CompletableFuture 사용: 진정한 비동기 처리
     * - aiMealClient.sendRequestAsync() 사용: .block() 제거
     */
    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncVisionAnalysis(Long userId, String base64Image) {
        return mealVisionService.asyncVisionAnalysis(userId, base64Image);
    }

    /**
     * [비동기 AI 심층 상담]
     * 
     * 변경 사항:
     * - @Transactional 제거: mealTargetService.updateAiFeedback()에 이미 @Transactional 있음
     * - CompletableFuture 사용: 진정한 비동기 처리
     * - aiMealClient.sendRequestAsync() 사용: .block() 제거
     */
    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncDeepAdvice(Long userId, LocalDate date) {
        return mealAdviceService.asyncDeepAdvice(userId, date);
    }

    /**
     * [비동기 식단 재구성]
     * 
     * 변경 사항:
     * - @Transactional 제거: updatePlannedMeals()에 이미 @Transactional 있음
     * - CompletableFuture 사용: 진정한 비동기 처리
     * - aiMealClient.sendRequestAsync() 사용: .block() 제거
     */
    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncMealReplan(Long userId, LocalDate date) {
        return mealReplanService.asyncMealReplan(userId, date);
    }

    /**
     * [비동기 끼니 생략 재배분]
     * - 생략된 끼니의 영양(탄/단/지/칼로리)을 "남은 PLANNED 끼니"에 균등 분배하여
     *   추가 메뉴(PLANNED, isAdditional=true)로 저장합니다.
     * - 음식 선택은 템플릿 복제가 아니라 meal_foods(개별 음식 풀)에서 수행합니다.
     */
    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncRedistributeAfterMealTimeSkip(Long userId, LocalDate date, String skippedMealTime) {
        return mealReplanService.asyncRedistributeAfterMealTimeSkip(userId, date, skippedMealTime);
    }

    @Override
    public CompletableFuture<AiMealVisionFollowupDto.Response> visionFollowup(Long userId, AiMealVisionFollowupDto.Request request) {
        return mealVisionService.visionFollowup(userId, request);
    }

    /**
     * [식단 계획 업데이트]
     * 
     * 변경 사항:
     * - private → public Service 메서드로 변경
     * - @Transactional 추가: DB 저장 보장
     * - 트랜잭션 경계 명확화
     */
    @Override
    @Transactional
    public void updatePlannedMeals(Long userId, LocalDate date, List<MealDto> newPlans) {
        mealPlanService.updatePlannedMeals(userId, date, newPlans);
    }



    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Integer> asyncGeneratePlanFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType) {
        return mealPlanGenerationService.asyncGeneratePlanFromAiChat(userId, startDate, periodDays, goalType);
    }

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Integer> asyncGeneratePlanFillMissingFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType) {
        return mealPlanGenerationService.asyncGeneratePlanFillMissingFromAiChat(userId, startDate, periodDays, goalType);
    }

    @Override
    @Transactional
    public String applyVisionAdd(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        return mealVisionService.applyVisionAdd(userId, date, mealTimeOrNull, analyzedFood);
    }

    @Override
    @Transactional
    public String applyVisionReplace(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        return mealVisionService.applyVisionReplace(userId, date, mealTimeOrNull, analyzedFood);
    }

    @Override
    @Transactional
    public String toggleMealTimeComplete(Long userId, LocalDate date, String mealTime) {
        return mealIntakeService.toggleMealTimeComplete(userId, date, mealTime);
    }

    @Override
    @Transactional
    public String toggleMealTimeSkip(Long userId, LocalDate date, String mealTime) {
        return mealIntakeService.toggleMealTimeSkip(userId, date, mealTime);
    }

    @Override
    @Transactional
    public String toggleItemByFoodName(Long userId, LocalDate date, String mealTimeOrNull, String foodName, String mode) {
        return mealIntakeService.toggleItemByFoodName(userId, date, mealTimeOrNull, foodName, mode);
    }

    // (Vision 관련 유틸은 MealVisionServiceImpl로 이동)
}