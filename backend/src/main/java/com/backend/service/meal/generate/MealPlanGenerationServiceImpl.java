package com.backend.service.meal.generate;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.MealDto;
import com.backend.dto.meal.MealTargetDto;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.plan.MealPlanService;
import com.backend.service.meal.target.MealTargetService;
import com.backend.service.meal.ws.MealWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealPlanGenerationServiceImpl implements MealPlanGenerationService {

    private final MealSearch mealSearch;
    private final AiMealClient aiMealClient;
    private final com.backend.service.memberinfo.MemberInfoBodyService memberInfoBodyService;
    private final MealTargetService mealTargetService;
    private final MealPlanService mealPlanService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MealWsPublisher mealWsPublisher;

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Integer> asyncGeneratePlanFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType) {
        int days = periodDays != null ? periodDays : 1;
        LocalDate start = LocalDate.now(); // 정책: 무조건 오늘부터
        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (0%)");

        String requestType = _requestType(days);

        MemberInfoBodyResponseDTO latestBody = _safeLatestBody(userId);

        String normalizedGoal = _normalizeGoal(goalType, latestBody);
        Integer age = latestBody != null ? _ageFromBirthDate(latestBody.getBirthDate()) : null;
        String gender = latestBody != null ? latestBody.getGender() : null;
        Double height = latestBody != null ? latestBody.getHeight() : null;
        Double weight = latestBody != null ? latestBody.getWeight() : null;

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType(requestType)
                .profile(AiMealRequestDto.UserProfile.builder()
                        .userId(userId)
                        .age(age)
                        .gender(gender)
                        .height(height)
                        .weight(weight)
                        .activityLevel("MODERATE")
                        .skeletalMuscleMass(latestBody != null ? latestBody.getSkeletalMuscleMass() : null)
                        .bodyFatPercent(latestBody != null ? latestBody.getBodyFatPercent() : null)
                        .bodyFatMass(latestBody != null ? latestBody.getBodyFatMass() : null)
                        .targetWeight(latestBody != null ? latestBody.getTargetWeight() : null)
                        .weightControl(latestBody != null ? latestBody.getWeightControl() : null)
                        .fatControl(latestBody != null ? latestBody.getFatControl() : null)
                        .muscleControl(latestBody != null ? latestBody.getMuscleControl() : null)
                        .build())
                .goal(AiMealRequestDto.GoalSpec.builder()
                        .goalType(normalizedGoal)
                        .mealCount(3)
                        .periodDays(days)
                        .startDate(start.toString())
                        .build())
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenApply(response -> {
                    List<MealDto> plans = response.getSuggestedMeals();
                    if (plans == null || plans.isEmpty()) {
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                        return 0;
                    }

                    Map<LocalDate, List<MealDto>> grouped = plans.stream()
                            .filter(p -> p.getMealDate() != null)
                            .collect(Collectors.groupingBy(MealDto::getMealDate));

                    int totalSteps = 1 + grouped.keySet().size();
                    int completed = 1;
                    int percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");

                    for (var entry : grouped.entrySet()) {
                        // 덮어쓰기 생성: 추가 메뉴 여부 상관없이 "미완료"는 전부 삭제 후 새 계획 저장 (완료는 보존)
                        mealPlanService.overwritePlannedMealsKeepEaten(userId, entry.getKey(), entry.getValue());
                        completed++;
                        percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                        if (percent >= 100) percent = 99;
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");
                    }

                    if (response.getTarget() != null) {
                        Integer tCal = response.getTarget().getTargetCalories();
                        Integer tCarb = response.getTarget().getTargetCarbs();
                        Integer tProt = response.getTarget().getTargetProtein();
                        Integer tFat = response.getTarget().getTargetFat();
                        if (tCal != null && tCal > 0) {
                            plans.stream()
                                    .map(MealDto::getMealDate)
                                    .filter(java.util.Objects::nonNull)
                                    .distinct()
                                    .forEach(mealDate -> {
                                        try {
                                            mealTargetService.updateTarget(
                                                    userId,
                                                    MealTargetDto.builder()
                                                            .targetDate(mealDate)
                                                            .goalType(normalizedGoal)
                                                            .goalCal(tCal)
                                                            .goalCarbs(tCarb)
                                                            .goalProtein(tProt)
                                                            .goalFat(tFat)
                                                            .build()
                                            );
                                        } catch (Exception e) {
                                            log.warn("[Meal] 목표치 저장 실패 - User: {}, Date: {}", userId, mealDate, e);
                                        }
                                    });
                        }
                    }

                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                    mealWsPublisher.publishMealChangedAfterCommit(userId);
                    log.info("[Meal] 식단 생성 완료 - User: {}, Days: {}, Count: {}", userId, days, plans.size());
                    return plans.size();
                })
                .exceptionally(ex -> {
                    log.error("[Meal] AI 식단 생성 실패: ", ex);
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId,
                            "식단 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                    return 0;
                });
    }

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Integer> asyncGeneratePlanFillMissingFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType) {
        int days = periodDays != null ? periodDays : 1;
        LocalDate start = LocalDate.now(); // 정책: 무조건 오늘부터
        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (0%)");

        String requestType = _requestType(days);
        MemberInfoBodyResponseDTO latestBody = _safeLatestBody(userId);

        String normalizedGoal = _normalizeGoal(goalType, latestBody);
        Integer age = latestBody != null ? _ageFromBirthDate(latestBody.getBirthDate()) : null;
        String gender = latestBody != null ? latestBody.getGender() : null;
        Double height = latestBody != null ? latestBody.getHeight() : null;
        Double weight = latestBody != null ? latestBody.getWeight() : null;

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType(requestType)
                .profile(AiMealRequestDto.UserProfile.builder()
                        .userId(userId)
                        .age(age)
                        .gender(gender)
                        .height(height)
                        .weight(weight)
                        .activityLevel("MODERATE")
                        .skeletalMuscleMass(latestBody != null ? latestBody.getSkeletalMuscleMass() : null)
                        .bodyFatPercent(latestBody != null ? latestBody.getBodyFatPercent() : null)
                        .bodyFatMass(latestBody != null ? latestBody.getBodyFatMass() : null)
                        .targetWeight(latestBody != null ? latestBody.getTargetWeight() : null)
                        .weightControl(latestBody != null ? latestBody.getWeightControl() : null)
                        .fatControl(latestBody != null ? latestBody.getFatControl() : null)
                        .muscleControl(latestBody != null ? latestBody.getMuscleControl() : null)
                        .build())
                .goal(AiMealRequestDto.GoalSpec.builder()
                        .goalType(normalizedGoal)
                        .mealCount(3)
                        .periodDays(days)
                        .startDate(start.toString())
                        .build())
                .build();

        LocalDate end = start.plusDays(Math.max(1, days) - 1L);
        java.util.Set<LocalDate> existingPlannedDates = mealSearch.findMealsBetweenDates(userId, start, end).stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .map(Meal::getMealDate)
                .collect(Collectors.toSet());

        return aiMealClient.sendRequestAsync(request)
                .thenApply(response -> {
                    List<MealDto> plans = response.getSuggestedMeals();
                    if (plans == null || plans.isEmpty()) {
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                        mealWsPublisher.publishMealChangedAfterCommit(userId);
                        return 0;
                    }

                    Map<LocalDate, List<MealDto>> grouped = plans.stream()
                            .filter(p -> p.getMealDate() != null)
                            .collect(Collectors.groupingBy(MealDto::getMealDate));

                    List<LocalDate> datesToSave = grouped.keySet().stream()
                            .filter(d -> !existingPlannedDates.contains(d))
                            .sorted()
                            .toList();

                    int totalSteps = 1 + datesToSave.size();
                    int completed = 1;
                    int percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");

                    for (var date : datesToSave) {
                        mealPlanService.updatePlannedMeals(userId, date, grouped.get(date));
                        completed++;
                        percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                        if (percent >= 100) percent = 99;
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");
                    }

                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                    mealWsPublisher.publishMealChangedAfterCommit(userId);
                    return plans.size();
                })
                .exceptionally(ex -> {
                    log.error("[Meal] AI 식단 생성(빈날만) 실패: ", ex);
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId,
                            "식단 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                    return 0;
                });
    }

    private MemberInfoBodyResponseDTO _safeLatestBody(Long userId) {
        try {
            return memberInfoBodyService.getLatest(userId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String _requestType(int days) {
        if (days == 7) return "GENERATE_WEEK";
        if (days == 30) return "GENERATE_MONTH";
        if (days > 1) return "GENERATE_DAYS";
        return "GENERATE";
    }

    private String _normalizeGoal(String goalType, MemberInfoBodyResponseDTO latestBody) {
        String goalCandidate = goalType;
        if (goalCandidate == null || goalCandidate.isBlank() || goalCandidate.equalsIgnoreCase("null") || goalCandidate.equalsIgnoreCase("MAINTAIN")) {
            if (latestBody != null && latestBody.getExercisePurpose() != null) {
                goalCandidate = latestBody.getExercisePurpose().name();
            }
        }
        return switch (goalCandidate == null ? "" : goalCandidate.toUpperCase()) {
            case "DIET", "WEIGHT_LOSS" -> "DIET";
            case "BULK_UP", "BULK" -> "BULK_UP";
            case "MAINTAIN" -> "MAINTAIN";
            default -> "MAINTAIN";
        };
    }

    private Integer _ageFromBirthDate(String birthDate) {
        try {
            if (birthDate == null || birthDate.isBlank()) return null;
            LocalDate bd = LocalDate.parse(birthDate.trim());
            return Period.between(bd, LocalDate.now()).getYears();
        } catch (Exception e) {
            return null;
        }
    }
}


