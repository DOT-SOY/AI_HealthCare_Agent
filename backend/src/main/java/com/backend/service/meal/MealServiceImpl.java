package com.backend.service.meal;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.dto.meal.*;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private final MealRepository mealRepository;
    private final MealSearch mealSearch;
    private final MealTargetService mealTargetService;
    private final AiMealClient aiMealClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.backend.service.memberinfo.MemberInfoBodyService memberInfoBodyService;
    private final MealAiContextService mealAiContextService;
    private final PlatformTransactionManager transactionManager;

    /**
     * [중요] 실시간 갱신 신호는 "트랜잭션 커밋 이후"에 보내야 합니다.
     * - 커밋 이전에 /topic/meal/changed 를 보내면, 프론트가 즉시 reload를 호출했을 때
     *   아직 DB에 반영되지 않아 "새로고침해야 보이는" 현상이 발생할 수 있습니다.
     */
    private void publishMealChangedAfterCommit(Long userId) {
        if (userId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/meal/changed/" + userId, "reload");
                }
            });
        } else {
            messagingTemplate.convertAndSend("/topic/meal/changed/" + userId, "reload");
        }
    }

    /**
     * [대시보드 통합 조립]
     * 목표(Target) + 식단(Meal) + 변동 분석(Analysis) 데이터를 하나의 세트로 조립합니다.
     */
    @Override
    public MealDashboardDto getMealDashboard(Long userId, LocalDate date) {
        log.info("[Dashboard] 데이터 조립 시작 - User: {}, Date: {}", userId, date);
        
        MealDashboardDto dashboardDto = MealDashboardDto.builder()
                .date(date.toString())
                .meals(new ArrayList<>())
                .analysisComments(new ArrayList<>())
                .build();

        // 1. 목표 달성률 및 그래프 데이터 계산 (MealTargetService 협력)
        mealTargetService.getNutritionAchievement(userId, date, dashboardDto);

        // 2. 당일 식단 리스트 조회
        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date);
        dashboardDto.setMeals(meals.stream()
                .map(MealDto::fromEntity)
                .collect(Collectors.toList()));

        // 분석 리포트 제거 (불필요한 긴 코드)
        dashboardDto.setAnalysisComments(new ArrayList<>());

        log.info("[Dashboard] 조립 완료");
        return dashboardDto;
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

    /**
     * [목표치 자동 생성]
     * - 목표가 없으면 중단하지 않고, memberinfo(goalType/키/몸무게/성별/나이) 기반으로 "일반적인" 목표치를 생성합니다.
     * - 정책(요구사항): DIET는 -15~20%, BULK_UP은 +15~20%, MAINTAIN은 기준치 유지.
     */
    @Transactional
    protected MealTargetDto ensureTargetAuto(Long userId, LocalDate date) {
        MealTargetDto existing = mealTargetService.getTargetByDate(userId, date);
        if (existing != null) return existing;

        MemberInfoBodyResponseDTO body = null;
        try {
            body = memberInfoBodyService.getLatest(userId);
        } catch (Exception ignored) {
            body = null;
        }

        String goalType = "MAINTAIN";
        if (body != null && body.getExercisePurpose() != null) {
            goalType = body.getExercisePurpose().name();
        }
        goalType = goalType == null || goalType.isBlank() ? "MAINTAIN" : goalType.trim().toUpperCase();
        if (!goalType.equals("DIET") && !goalType.equals("BULK_UP") && !goalType.equals("MAINTAIN")) {
            goalType = "MAINTAIN";
        }

        Double weight = body != null ? body.getWeight() : null; // kg
        Double height = body != null ? body.getHeight() : null; // cm
        Integer age = _ageFromBirthDate(body != null ? body.getBirthDate() : null);
        String gender = body != null ? body.getGender() : null; // "MALE"/"FEMALE" 예상

        double bmr;
        if (weight != null && height != null && age != null && weight > 0 && height > 0 && age > 0) {
            boolean isMale = gender != null && gender.trim().equalsIgnoreCase("MALE");
            boolean isFemale = gender != null && gender.trim().equalsIgnoreCase("FEMALE");
            // Mifflin-St Jeor
            double base = (10.0 * weight) + (6.25 * height) - (5.0 * age);
            bmr = base + (isMale ? 5.0 : isFemale ? -161.0 : -78.0); // 성별 미상은 중간값
        } else {
            // 최소 안전값(정보 부족)
            bmr = 1500.0;
        }

        // 활동계수(정보가 없으므로 기본값): 1.55 (보통 활동)
        double tdee = bmr * 1.55;

        // goalType에 따른 칼로리 조정(범위 내 보수적 값 사용)
        double factor = switch (goalType) {
            case "DIET" -> 0.85;     // -15%
            case "BULK_UP" -> 1.15;  // +15%
            default -> 1.00;
        };
        int cal = (int) Math.round(tdee * factor);
        cal = Math.max(1200, cal);

        // 매크로(grams): 단백질/지방은 체중 기반, 탄수화물은 잔여 칼로리
        double w = weight != null && weight > 0 ? weight : 70.0;
        double proteinPerKg = switch (goalType) {
            case "DIET" -> 1.8;
            case "BULK_UP" -> 2.0;
            default -> 1.6;
        };
        double fatPerKg = switch (goalType) {
            case "DIET" -> 0.7;
            case "BULK_UP" -> 0.9;
            default -> 0.8;
        };

        int protein = (int) Math.round(w * proteinPerKg);
        int fat = (int) Math.round(w * fatPerKg);
        int remainCal = cal - (protein * 4) - (fat * 9);
        int carbs = Math.max(0, (int) Math.round(remainCal / 4.0));

        MealTargetDto dto = MealTargetDto.builder()
                .targetDate(date)
                .goalType(goalType)
                .goalCal(cal)
                .goalCarbs(carbs)
                .goalProtein(protein)
                .goalFat(fat)
                .build();
        return mealTargetService.updateTarget(userId, dto);
    }

    @Override
    @Transactional
    public MealDto registerAdditionalMeal(Long userId, MealDto mealDto) {
        log.info("[Meal] 추가 식단 등록 - User: {}, Food: {}", userId, mealDto.getFoodName());
        mealDto.setIsAdditional(true);
        mealDto.setStatus(Meal.MealStatus.EATEN.name());
        Meal saved = mealRepository.save(mealDto.toEntity(userId));
        publishMealChangedAfterCommit(userId);
        return MealDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public MealDto updateMeal(Long scheduleId, MealDto mealDto) {
        log.info("[Meal] 식단 정보 수정 - ID: {}", scheduleId);
        Meal meal = mealRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("해당 식단 데이터를 찾을 수 없습니다."));

        // Original은 보존, 실측값만 업데이트하여 분석 근거 유지
        meal.updateMealInfo(
                mealDto.getFoodName(), mealDto.getServingSize(),
                mealDto.getCalories(), mealDto.getCarbs(),
                mealDto.getProtein(), mealDto.getFat(),
                Meal.MealStatus.valueOf(mealDto.getStatus())
        );
        publishMealChangedAfterCommit(meal.getUserId());
        return MealDto.fromEntity(meal);
    }

    @Override
    @Transactional
    public void toggleMealStatus(Long scheduleId, String status) {
        mealRepository.findById(scheduleId).ifPresent(m -> {
            log.info("[Meal] 상태 변경 - ID: {}, Status: {}", scheduleId, status);
            m.changeStatus(Meal.MealStatus.valueOf(status));
            // [중요] 식단 변경 사항 전파 (프론트 자동 갱신용)
            publishMealChangedAfterCommit(m.getUserId());
        });
    }

    @Override
    @Transactional
    public void removeOrSkipMeal(Long scheduleId, boolean isPermanentDelete) {
        mealRepository.findById(scheduleId).ifPresent(m -> {
            if (isPermanentDelete || m.getIsAdditional()) {
                log.info("[Meal] 데이터 영구 삭제 - ID: {}", scheduleId);
                mealRepository.delete(m);
            } else {
                log.info("[Meal] 계획 식단 건너뛰기 처리 - ID: {}", scheduleId);
                m.changeStatus(Meal.MealStatus.SKIPPED);
            }
            publishMealChangedAfterCommit(m.getUserId());
        });
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
        log.info("[Async] Vision AI 분석 요청 - User: {}", userId);
        
        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("ANALYZE_IMAGE")
                .foodImageBase64(base64Image)
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    // Vision 결과를 "pending"으로 저장하여, 다음 사용자 발화(전역 채팅)에서 자연어로 추가/변경/취소를 처리할 수 있게 함
                    try {
                        String foodName = "알 수 없음";
                        int calories = 0, carbs = 0, protein = 0, fat = 0;
                        if (response != null && response.getAnalyzedFood() != null) {
                            var af = response.getAnalyzedFood();
                            foodName = af.getFoodName() != null ? af.getFoodName() : "알 수 없음";
                            calories = af.getCalories() != null ? af.getCalories() : 0;
                            carbs = af.getCarbs() != null ? af.getCarbs() : 0;
                            protein = af.getProtein() != null ? af.getProtein() : 0;
                            fat = af.getFat() != null ? af.getFat() : 0;
                        }
                        java.util.Map<String, Object> analyzedFood = new java.util.HashMap<>();
                        analyzedFood.put("foodName", foodName);
                        analyzedFood.put("calories", calories);
                        analyzedFood.put("carbs", carbs);
                        analyzedFood.put("protein", protein);
                        analyzedFood.put("fat", fat);
                        java.util.Map<String, Object> pendingData = new java.util.HashMap<>();
                        pendingData.put("analyzedFood", analyzedFood);
                        pendingData.put("defaultDate", java.time.LocalDate.now().toString());
                        pendingData.put("defaultMealTime", _inferMealTimeFromNow().name());
                        mealAiContextService.setPending(userId, "VISION_FOLLOWUP", pendingData);
                    } catch (Exception ignored) {
                        // pending 저장 실패는 UX 치명도가 낮으므로 무시(WS 결과는 계속 전달)
                    }
                    // WebSocket 전역 경로 푸시: /topic/meal/vision/{userId}
                    Object payload = java.util.Map.of(
                            "foodName", "알 수 없음",
                            "calories", 0,
                            "carbs", 0,
                            "protein", 0,
                            "fat", 0
                    );
                    if (response != null && response.getAnalyzedFood() != null) {
                        payload = response.getAnalyzedFood();
                    }
                    messagingTemplate.convertAndSend("/topic/meal/vision/" + userId, payload);
                    log.info("[Async] Vision 분석 결과 전송 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] Vision 분석 실패: ", throwable);
                    messagingTemplate.convertAndSend("/topic/meal/error/" + userId, "이미지 분석 중 시스템 오류가 발생했습니다.");
                    return null;
                });
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
        log.info("[Async] AI 심층 상담 요청 - User: {}, Date: {}", userId, date);
        
        List<Meal> currentMeals = mealSearch.findMealsByDateAndUser(userId, date);
        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("ADVICE")
                .currentMeals(currentMeals.stream().map(MealDto::fromEntity).toList())
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    // 분석 결과를 DB에 저장하여 탭 전환 시에도 유지되게 함
                    // mealTargetService.updateAiFeedback()에 이미 @Transactional 있음
                    mealTargetService.updateAiFeedback(userId, date, response.getAdviceComment());

                    // 실시간 결과 전송
                    messagingTemplate.convertAndSend("/topic/meal/advice/" + userId, response.getAdviceComment());
                    log.info("[Async] 심층 상담 완료 및 DB 저장 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] 심층 상담 실패: ", throwable);
                    return null;
                });
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
        log.info("[Async] 식단 재구성(Replan) 시작 - User: {}, Date: {}", userId, date);
        
        // 1) 해당 날짜의 현재 식단 조회하여 남은 PLANNED 끼니 확인
        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> remainingPlanned = existing.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .filter(m -> !Boolean.TRUE.equals(m.getIsAdditional()))
                .toList();
        
        // 2) 남은 PLANNED 끼니가 없으면 재구성하지 않고 종료
        if (remainingPlanned.isEmpty()) {
            String msg = "잔여 식사가 없어 오늘 식사가 여기서 끝난다";
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, msg);
            log.info("[Async] 식단 재구성 건너뜀 - 남은 PLANNED 끼니 없음");
            return CompletableFuture.completedFuture(null);
        }
        
        // 3) EATEN/SKIPPED(추가섭취 아님) 끼니는 제외하고, 남은 끼니만 current_meals로 전달
        // AI 서버의 replan_meal_plan은 current_meals를 받아서 EATEN/SKIPPED 끼니를 제외하고 남은 끼니만 재구성함
        // 여기서는 전체 existing을 전달하되, AI 서버가 자체적으로 필터링하도록 함
        List<MealDto> currentMealsForReplan = existing.stream()
                .map(MealDto::fromEntity)
                .collect(Collectors.toList());
        
        // 4) 잔여 영양소 계산 (목표 - 현재 섭취량) - 생략된 끼니의 영양성분을 고려
        MealTargetDto remaining = mealTargetService.calculateRemainingNutrients(userId, date);
        if (remaining == null) {
            remaining = mealTargetService.getTargetByDate(userId, date);
        }
        if (remaining == null) {
            try {
                MealTargetDto created = ensureTargetAuto(userId, date);
                if (created != null) {
                    String notice = String.format(
                            "오늘 목표치가 없어 '%s' 기준으로 자동 설정한 뒤 재정비를 진행할게요.",
                            created.getGoalType() != null ? created.getGoalType() : "MAINTAIN"
                    );
                    messagingTemplate.convertAndSend(
                            "/topic/meal/replan/" + userId,
                            notice
                    );
                    remaining = mealTargetService.calculateRemainingNutrients(userId, date);
                    if (remaining == null) remaining = created;
                }
            } catch (Exception ignored) {
                remaining = null;
            }
        }
        if (remaining == null) {
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "오늘 목표치가 설정되어 있지 않아 재정비를 진행할 수 없습니다.");
            return CompletableFuture.completedFuture(null);
        } 

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("REPLAN")
                .goal(AiMealRequestDto.GoalSpec.builder()
                        .targetCalories(remaining.getGoalCal())
                        .targetCarbs(remaining.getGoalCarbs())
                        .targetProtein(remaining.getGoalProtein())
                        .targetFat(remaining.getGoalFat())
                        .build())
                .currentMeals(currentMealsForReplan)
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    List<MealDto> suggested = response.getSuggestedMeals();
                    if (suggested == null) suggested = List.of();

                    // "남은 끼니" 기준: non-additional PLANNED가 남아있는 끼니만 재정비 대상으로 삼습니다.
                    // (같은 끼니 안에 SKIPPED가 섞여 있어도 PLANNED가 있으면 남은 끼니입니다.)
                    java.util.Set<String> remainingTimes = currentMealsForReplan.stream()
                            .filter(m -> m.getStatus() != null
                                    && m.getStatus().equalsIgnoreCase(Meal.MealStatus.PLANNED.name())
                                    && (m.getIsAdditional() == null || !m.getIsAdditional()))
                            .map(MealDto::getMealTime)
                            .filter(java.util.Objects::nonNull)
                            .map(String::toUpperCase)
                            .collect(java.util.stream.Collectors.toSet());

                    List<MealDto> filtered = suggested.stream()
                            .filter(m -> m.getMealTime() != null && remainingTimes.contains(m.getMealTime().toUpperCase()))
                            .toList();

                    // 안전장치: 제안이 비어있으면 DB를 건드리지 않고 원인을 사용자에게 알림
                    if (filtered.isEmpty()) {
                        messagingTemplate.convertAndSend("/topic/meal/replan/" + userId,
                                "재정비를 시도했지만 제안할 남은 끼니가 없어요. (이미 모두 완료/생략 상태이거나 제안 생성이 실패했을 수 있어요)");
                        log.warn("[Async] 식단 재구성 결과가 비어있어 updatePlannedMeals를 스킵합니다. userId={}, date={}", userId, date);
                        return;
                    }

                    // PLANNED 상태의 계획만 교체
                    // updatePlannedMeals()에 이미 @Transactional 있음
                    updatePlannedMeals(userId, date, filtered);

                    // 재구성 완료 후 대시보드 자동 갱신은 updatePlannedMeals()의 afterCommit publish로 처리
                    messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "남은 일정이 최적으로 재구성되었습니다.");
                    log.info("[Async] 식단 재구성 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] 식단 재구성 실패: ", throwable);
                    messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, 
                            "식단 재구성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                    return null;
                });
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
        try {
            if (userId == null || date == null || skippedMealTime == null || skippedMealTime.isBlank()) {
                return CompletableFuture.completedFuture(null);
            }

            Meal.MealTime skippedMt;
            try {
                skippedMt = Meal.MealTime.valueOf(skippedMealTime.trim().toUpperCase());
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "끼니 정보를 이해하지 못했어요. (아침/점심/저녁)");
                return CompletableFuture.completedFuture(null);
            }

            List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);
            List<Meal> skippedNonAdditional = dayMeals.stream()
                    .filter(m -> m.getMealTime() == skippedMt)
                    .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                    .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED)
                    .toList();

            boolean hasNonAdditionalPlanned = dayMeals.stream()
                    .anyMatch(m -> m.getMealTime() == skippedMt
                            && (m.getIsAdditional() == null || !m.getIsAdditional())
                            && m.getStatus() == Meal.MealStatus.PLANNED);
            boolean hasNonAdditionalEaten = dayMeals.stream()
                    .anyMatch(m -> m.getMealTime() == skippedMt
                            && (m.getIsAdditional() == null || !m.getIsAdditional())
                            && m.getStatus() == Meal.MealStatus.EATEN);

            boolean isWholeMealTimeSkipped = !skippedNonAdditional.isEmpty() && !hasNonAdditionalPlanned && !hasNonAdditionalEaten;
            if (!isWholeMealTimeSkipped) {
                // 끼니 전체 생략이 아닌 경우(대체 등)엔 재배분하지 않음
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "해당 끼니가 '전체 생략' 상태가 아니라 재배분을 진행하지 않았어요.");
                return CompletableFuture.completedFuture(null);
            }

            // 재구성(재배분)은 "생략된 끼니 kcal"가 아니라 "하루 목표치"를 기준으로 해야 합니다.
            // 현재(생략 제외) 계획+섭취 합계를 목표치에 맞추도록, 남은 끼니에 추가 메뉴를 배치합니다.
            MealTargetDto target = mealTargetService.getTargetByDate(userId, date);
            if (target == null) {
                try {
                    MealTargetDto created = ensureTargetAuto(userId, date);
                    if (created != null) target = created;
                } catch (Exception ignored) {
                    target = null;
                }
            }
            if (target == null || target.getGoalCal() == null || target.getGoalCal() <= 0) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "오늘 목표치가 없어 재정비를 진행할 수 없습니다.");
                return CompletableFuture.completedFuture(null);
            }

            // 현재(생략 제외) 계획/섭취 합계 = status != SKIPPED
            int currentCal = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0)
                    .sum();
            int currentCarb = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0)
                    .sum();
            int currentProt = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0)
                    .sum();
            int currentFat = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getFat() != null ? m.getFat() : 0)
                    .sum();

            int gapCal = Math.max(0, (target.getGoalCal() != null ? target.getGoalCal() : 0) - currentCal);
            int gapCarb = Math.max(0, (target.getGoalCarbs() != null ? target.getGoalCarbs() : 0) - currentCarb);
            int gapProt = Math.max(0, (target.getGoalProtein() != null ? target.getGoalProtein() : 0) - currentProt);
            int gapFat = Math.max(0, (target.getGoalFat() != null ? target.getGoalFat() : 0) - currentFat);

            if (gapCal <= 0 && gapCarb <= 0 && gapProt <= 0 && gapFat <= 0) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "이미 하루 목표치에 근접해 있어 추가 메뉴를 만들지 않았어요.");
                return CompletableFuture.completedFuture(null);
            }

            // 남은 끼니(아직 완료되지 않은 끼니) = non-additional PLANNED가 남아있는 끼니
            Set<Meal.MealTime> remainingTimes = dayMeals.stream()
                    .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                    .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                    .map(Meal::getMealTime)
                    .filter(mt -> mt == Meal.MealTime.BREAKFAST || mt == Meal.MealTime.LUNCH || mt == Meal.MealTime.DINNER)
                    .filter(mt -> mt != skippedMt)
                    .collect(Collectors.toSet());

            List<Meal.MealTime> orderedTimes = List.of(Meal.MealTime.BREAKFAST, Meal.MealTime.LUNCH, Meal.MealTime.DINNER).stream()
                    .filter(remainingTimes::contains)
                    .toList();

            if (orderedTimes.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "남은 끼니가 없어 재배분을 진행할 수 없어요.");
                return CompletableFuture.completedFuture(null);
            }

            // 제외 목록: 밥/국/찌개 + 생략된 끼니의 메뉴
            List<String> excludeKeywords = List.of("밥", "국", "찌개");
            java.util.Set<String> excludeFoodNames = skippedNonAdditional.stream()
                    .map(Meal::getFoodName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            int n = orderedTimes.size();
            int baseCal = gapCal / n, remCal = gapCal % n;
            int baseCarb = gapCarb / n, remCarb = gapCarb % n;
            int baseProt = gapProt / n, remProt = gapProt % n;
            int baseFat = gapFat / n, remFat = gapFat % n;

            java.util.Map<Meal.MealTime, List<MealDto>> picksByTime = new java.util.LinkedHashMap<>();

            for (int idx = 0; idx < orderedTimes.size(); idx++) {
                Meal.MealTime mt = orderedTimes.get(idx);

                int tCal = baseCal + (idx < remCal ? 1 : 0);
                int tCarb = baseCarb + (idx < remCarb ? 1 : 0);
                int tProt = baseProt + (idx < remProt ? 1 : 0);
                int tFat = baseFat + (idx < remFat ? 1 : 0);

                AiMealRequestDto request = AiMealRequestDto.builder()
                        .requestType("PICK_FOODS")
                        .goal(AiMealRequestDto.GoalSpec.builder()
                                .targetCalories(Math.max(0, tCal))
                                .targetCarbs(Math.max(0, tCarb))
                                .targetProtein(Math.max(0, tProt))
                                .targetFat(Math.max(0, tFat))
                                .excludeKeywords(excludeKeywords)
                                .excludeFoodNames(new java.util.ArrayList<>(excludeFoodNames))
                                .minItems(1)
                                .maxItems(3)
                                .build())
                        .build();

                try {
                    var resp = aiMealClient.sendRequestAsync(request).join();
                    List<MealDto> suggested = resp != null ? resp.getSuggestedMeals() : null;
                    if (suggested == null) suggested = List.of();
                    // 안전 필터: foodName 없는 항목 제거 + 최대 3개
                    List<MealDto> picked = suggested.stream()
                            .filter(m -> m != null && m.getFoodName() != null && !m.getFoodName().isBlank())
                            .limit(3)
                            .toList();

                    picksByTime.put(mt, new java.util.ArrayList<>(picked));
                    // 다음 끼니에서 중복을 줄이기 위해 이미 선택된 음식명도 제외 목록에 추가
                    picked.stream()
                            .map(MealDto::getFoodName)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .forEach(excludeFoodNames::add);
                } catch (Exception e) {
                    log.warn("[Meal] PICK_FOODS 실패 - userId={}, date={}, mealTime={}, err={}", userId, date, mt, e.getMessage());
                    picksByTime.put(mt, List.of());
                }
            }

            // [가드] 하루 목표치 허용범위(±10%) 상한을 넘기면, 고칼로리 추가 메뉴부터 제거하여 상한 안으로 맞춥니다.
            int goalCal = target.getGoalCal() != null ? target.getGoalCal() : 0;
            if (goalCal > 0) {
                int maxAllowed = (int) Math.round(goalCal * 1.10);
                int addedCal = picksByTime.values().stream()
                        .flatMap(List::stream)
                        .mapToInt(m -> m != null && m.getCalories() != null ? m.getCalories() : 0)
                        .sum();
                int finalCal = currentCal + addedCal;

                if (finalCal > maxAllowed) {
                    class PickRef {
                        final Meal.MealTime mt;
                        final MealDto dto;
                        final int cal;
                        PickRef(Meal.MealTime mt, MealDto dto, int cal) { this.mt = mt; this.dto = dto; this.cal = cal; }
                    }

                    java.util.List<PickRef> all = new java.util.ArrayList<>();
                    for (var entry : picksByTime.entrySet()) {
                        for (MealDto m : entry.getValue()) {
                            int c = (m != null && m.getCalories() != null) ? m.getCalories() : 0;
                            all.add(new PickRef(entry.getKey(), m, c));
                        }
                    }
                    all.sort((a, b) -> Integer.compare(b.cal, a.cal));

                    for (PickRef ref : all) {
                        if (finalCal <= maxAllowed) break;
                        var list = picksByTime.get(ref.mt);
                        if (list == null || list.isEmpty()) continue;
                        boolean removed = list.remove(ref.dto);
                        if (!removed) {
                            // 동등 객체 비교가 실패할 경우 대비: foodName+calories 기반으로 제거
                            for (int i = 0; i < list.size(); i++) {
                                MealDto x = list.get(i);
                                if (x == null) continue;
                                if (java.util.Objects.equals(x.getFoodName(), ref.dto.getFoodName())
                                        && java.util.Objects.equals(x.getCalories(), ref.dto.getCalories())) {
                                    list.remove(i);
                                    removed = true;
                                    break;
                                }
                            }
                        }
                        if (removed) {
                            finalCal -= ref.cal;
                        }
                    }
                }
            }

            boolean hasAnyPick = picksByTime.values().stream().anyMatch(list -> list != null && !list.isEmpty());
            if (!hasAnyPick) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "재배분할 추가 메뉴를 찾지 못했어요. 잠시 후 다시 시도해주세요.");
                return CompletableFuture.completedFuture(null);
            }

            // DB 반영은 트랜잭션으로 묶어서 처리
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                List<Meal> nowMeals = mealSearch.findMealsByDateAndUser(userId, date);

                // 이전 재배분 결과(추가 메뉴 PLANNED)는 모두 정리 후 다시 생성
                List<Meal> toDelete = nowMeals.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsAdditional()))
                        .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                        .toList();
                if (!toDelete.isEmpty()) {
                    mealRepository.deleteAll(toDelete);
                    mealRepository.flush();
                }

                for (var entry : picksByTime.entrySet()) {
                    Meal.MealTime mt = entry.getKey();
                    List<MealDto> picked = entry.getValue() != null ? entry.getValue() : List.of();
                    for (MealDto p : picked) {
                        String foodName = p.getFoodName();
                        if (foodName == null || foodName.isBlank()) continue;
                        String serving = (p.getServingSize() == null || p.getServingSize().isBlank()) ? "1인분" : p.getServingSize();

                        MealDto dto = MealDto.builder()
                                .mealDate(date)
                                .mealTime(mt.name())
                                .status(Meal.MealStatus.PLANNED.name())
                                .isAdditional(true)
                                .foodName(foodName)
                                .servingSize(serving)
                                .calories(p.getCalories())
                                .carbs(p.getCarbs())
                                .protein(p.getProtein())
                                .fat(p.getFat())
                                // original은 분석 UI/일관성 목적. 없으면 current 값으로 채움
                                .originalFoodName(p.getOriginalFoodName() != null ? p.getOriginalFoodName() : foodName)
                                .originalServingSize(p.getOriginalServingSize() != null ? p.getOriginalServingSize() : serving)
                                .originalCalories(p.getOriginalCalories() != null ? p.getOriginalCalories() : p.getCalories())
                                .originalCarbs(p.getOriginalCarbs() != null ? p.getOriginalCarbs() : p.getCarbs())
                                .originalProtein(p.getOriginalProtein() != null ? p.getOriginalProtein() : p.getProtein())
                                .originalFat(p.getOriginalFat() != null ? p.getOriginalFat() : p.getFat())
                                .build();

                        mealRepository.save(dto.toEntity(userId));
                    }
                }

                publishMealChangedAfterCommit(userId);
            });

            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "하루 목표치를 기준으로 남은 끼니에 재정비(추가메뉴)했어요.");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("[Async] 끼니 생략 재배분 실패:", e);
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "재배분 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<AiMealVisionFollowupDto.Response> visionFollowup(Long userId, AiMealVisionFollowupDto.Request request) {
        return aiMealClient.sendVisionFollowupAsync(request)
                .exceptionally(ex -> {
                    log.warn("[MealService] Vision followup failed: {}", ex.getMessage());
                    return new AiMealVisionFollowupDto.Response("ASK", null, "추가할까요, 변경할까요?");
                });
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
        log.info("[Meal] 식단 계획 업데이트 - User: {}, Date: {}", userId, date);
        
        // 1) 해당 날짜의 기존 식단 조회
        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);

        // 2) 새 계획이 내려온 끼니(MealTime) 집합 계산
        Set<Meal.MealTime> timesInNewPlan = newPlans.stream()
                .map(MealDto::getMealTime)
                .filter(Objects::nonNull)
                .map(t -> {
                    try {
                        return Meal.MealTime.valueOf(t.toUpperCase());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3) 삭제 대상:
        //    - 해당 날짜의 식단 중
        //    - 이번에 새 계획이 내려온 끼니에 속하는 것만
        //    - 로컬 폴더 버전: 모든 식단을 삭제 대상으로 고려 (PLANNED만이 아니라)
        // 3) 삭제 대상:
        // - 이번에 새 계획이 내려온 끼니에 속하면서
        // - non-additional 이고
        // - EATEN은 보존 (이미 먹은 것은 절대 삭제하지 않음)
        List<Meal> toDelete = existing.stream()
                .filter(m -> timesInNewPlan.isEmpty() || timesInNewPlan.contains(m.getMealTime()))
                .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                .filter(m -> m.getStatus() != Meal.MealStatus.EATEN)
                .toList();

        if (!toDelete.isEmpty()) {
            mealRepository.deleteAll(toDelete);
            mealRepository.flush();
        }

        // 4) AI가 제안한 새로운 계획들을 저장
        for (MealDto dto : newPlans) {
            if (dto.getMealDate() == null) {
                dto.setMealDate(date);
            }
            if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                dto.setStatus(Meal.MealStatus.PLANNED.name());
            }
            if (dto.getIsAdditional() == null) {
                dto.setIsAdditional(false);
            }
            Meal entity = dto.toEntity(userId);
            mealRepository.save(entity);
        }
        
        // [중요] 식단 변경 사항 전파 (프론트 자동 갱신용) - 커밋 이후 전송
        publishMealChangedAfterCommit(userId);
        
        log.info("[Meal] 식단 계획 업데이트 완료 - 삭제: {}개, 추가: {}개", toDelete.size(), newPlans.size());
    }



    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Integer> asyncGeneratePlanFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType) {
        int days = periodDays != null ? periodDays : 1;
        // 식단 생성은 무조건 오늘부터
        LocalDate start = LocalDate.now();
        // 진행률(실제 기반): AI 응답 1 step + 날짜별 저장 N step
        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (0%)");

        String requestType;
        if (days == 7) {
            requestType = "GENERATE_WEEK";
        } else if (days == 30) {
            requestType = "GENERATE_MONTH";
        } else if (days > 1) {
            requestType = "GENERATE_DAYS";
        } else {
            requestType = "GENERATE";
        }

        // 1) goalType: 사용자 발화(entities) 우선, 없으면 memberinfo.exercisePurpose 사용
        MemberInfoBodyResponseDTO latestBody = null;
        try {
            latestBody = memberInfoBodyService.getLatest(userId);
        } catch (Exception ignored) {
            latestBody = null;
        }

        String goalCandidate = goalType;
        if (goalCandidate == null || goalCandidate.isBlank() || goalCandidate.equalsIgnoreCase("null") || goalCandidate.equalsIgnoreCase("MAINTAIN")) {
            if (latestBody != null && latestBody.getExercisePurpose() != null) {
                goalCandidate = latestBody.getExercisePurpose().name();
            }
        }

        String normalizedGoal = switch (goalCandidate == null ? "" : goalCandidate.toUpperCase()) {
            case "DIET", "WEIGHT_LOSS" -> "DIET";
            case "BULK_UP", "BULK" -> "BULK_UP";
            case "MAINTAIN" -> "MAINTAIN";
            default -> "MAINTAIN";
        };

        // 2) profile: memberinfo(인바디) 기반으로 최대한 채워서 전달
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
                        .mealCount(3) // 화면이 아침/점심/저녁 기준이므로 기본 3끼
                        .periodDays(days)
                        .startDate(start.toString())
                        .build())
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenApply(response -> {
                    List<MealDto> plans = response.getSuggestedMeals();
                    if (plans == null || plans.isEmpty()) {
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, 
                                "식단 생성 완료! (100%)");
                        return 0;
                    }

                    // mealDate 기준으로 날짜별로 PLANNED 계획을 교체 저장 (저장 진행률 = 실제 근거)
                    var grouped = plans.stream()
                            .filter(p -> p.getMealDate() != null)
                            .collect(Collectors.groupingBy(MealDto::getMealDate));

                    int totalSteps = 1 + grouped.keySet().size(); // AI 응답 + 날짜별 저장
                    int completed = 1; // AI 응답 수신 완료
                    int percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");

                    for (var entry : grouped.entrySet()) {
                        updatePlannedMeals(userId, entry.getKey(), entry.getValue());
                        completed++;
                        percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                        if (percent >= 100) percent = 99; // 마지막 완료 메시지에서 100 처리
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");
                    }

                    // (선택) AI 서버가 목표치를 내려줬다면, 해당 기간 목표치도 함께 저장해 상단 목표치가 바로 정상 표시되게 함
                    if (response.getTarget() != null) {
                        Integer tCal = response.getTarget().getTargetCalories();
                        Integer tCarb = response.getTarget().getTargetCarbs();
                        Integer tProt = response.getTarget().getTargetProtein();
                        Integer tFat = response.getTarget().getTargetFat();
                        if (tCal != null && tCal > 0) {
                            // 생성된 날짜 목록을 기준으로 동일 목표치 저장
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

                    // 완료 알림(100%) - 같은 버블의 퍼센트가 100으로 마무리됨
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                    
                    // 식단 생성 완료 후 대시보드 자동 갱신을 위한 알림
                    publishMealChangedAfterCommit(userId);

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
        // 식단 생성은 무조건 오늘부터
        LocalDate start = LocalDate.now();
        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (0%)");

        // (기존 생성과 동일하게 requestType 결정)
        String requestType;
        if (days == 7) {
            requestType = "GENERATE_WEEK";
        } else if (days == 30) {
            requestType = "GENERATE_MONTH";
        } else if (days > 1) {
            requestType = "GENERATE_DAYS";
        } else {
            requestType = "GENERATE";
        }

        MemberInfoBodyResponseDTO latestBody = null;
        try {
            latestBody = memberInfoBodyService.getLatest(userId);
        } catch (Exception ignored) {
            latestBody = null;
        }

        String goalCandidate = goalType;
        if (goalCandidate == null || goalCandidate.isBlank() || goalCandidate.equalsIgnoreCase("null") || goalCandidate.equalsIgnoreCase("MAINTAIN")) {
            if (latestBody != null && latestBody.getExercisePurpose() != null) {
                goalCandidate = latestBody.getExercisePurpose().name();
            }
        }

        String normalizedGoal = switch (goalCandidate == null ? "" : goalCandidate.toUpperCase()) {
            case "DIET", "WEIGHT_LOSS" -> "DIET";
            case "BULK_UP", "BULK" -> "BULK_UP";
            case "MAINTAIN" -> "MAINTAIN";
            default -> "MAINTAIN";
        };

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

        // 기간 내 이미 PLANNED가 있는 날짜는 유지
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
                    
                    // 식단 생성 완료 후 대시보드 자동 갱신을 위한 알림
                    publishMealChangedAfterCommit(userId);
                        return 0;
                    }

                    // 날짜별로 PLANNED가 비어있는 날만 저장
                    Map<LocalDate, List<MealDto>> grouped = plans.stream()
                            .filter(p -> p.getMealDate() != null)
                            .collect(Collectors.groupingBy(MealDto::getMealDate));

                    // 실제 저장 대상 날짜만 뽑아서 progress 계산
                    List<LocalDate> datesToSave = grouped.keySet().stream()
                            .filter(d -> !existingPlannedDates.contains(d))
                            .sorted()
                            .toList();

                    int totalSteps = 1 + datesToSave.size(); // AI 응답 + 저장 대상 날짜 수
                    int completed = 1; // AI 응답 수신 완료
                    int percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");

                    for (var date : datesToSave) {
                        updatePlannedMeals(userId, date, grouped.get(date));
                        completed++;
                        percent = (int) Math.floor((completed * 100.0) / Math.max(1, totalSteps));
                        if (percent >= 100) percent = 99;
                        messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 중 (" + percent + "%)");
                    }

                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId, "식단 생성 완료! (100%)");
                    
                    // 식단 생성 완료 후 대시보드 자동 갱신을 위한 알림
                    publishMealChangedAfterCommit(userId);
                    return plans.size();
                })
                .exceptionally(ex -> {
                    log.error("[Meal] AI 식단 생성(빈날만) 실패: ", ex);
                    messagingTemplate.convertAndSend("/topic/meal/generate/" + userId,
                            "식단 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                    return 0;
                });
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

    private static Meal.MealTime _inferMealTimeFromNow() {
        // 05:00-11:59 breakfast, 12:00-16:59 lunch, 17:00-04:59 dinner
        java.time.LocalTime now = java.time.LocalTime.now();
        int mins = now.getHour() * 60 + now.getMinute();
        if (mins >= 300 && mins <= 719) return Meal.MealTime.BREAKFAST;
        if (mins >= 720 && mins <= 1019) return Meal.MealTime.LUNCH;
        return Meal.MealTime.DINNER;
    }

    @Override
    @Transactional
    public String applyVisionAdd(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        if (userId == null || date == null || analyzedFood == null || analyzedFood.isEmpty()) {
            return "이미지 분석 결과를 반영할 수 없어요. 다시 시도해주세요.";
        }

        Meal.MealTime mealTime = null;
        if (mealTimeOrNull != null && !mealTimeOrNull.isBlank()) {
            try {
                mealTime = Meal.MealTime.valueOf(mealTimeOrNull.trim().toUpperCase());
            } catch (Exception ignored) {
                mealTime = null;
            }
        }

        String foodName = String.valueOf(analyzedFood.getOrDefault("foodName", "알 수 없음"));
        Integer calories = getIntValue(analyzedFood.get("calories"));
        Integer carbs = getIntValue(analyzedFood.get("carbs"));
        Integer protein = getIntValue(analyzedFood.get("protein"));
        Integer fat = getIntValue(analyzedFood.get("fat"));

        MealDto dto = MealDto.builder()
                .foodName(foodName)
                .calories(calories)
                .carbs(carbs)
                .protein(protein)
                .fat(fat)
                .mealDate(date)
                .mealTime(mealTime != null ? mealTime.name() : "DINNER")
                .status("EATEN")
                .isAdditional(true)
                .build();

        registerAdditionalMeal(userId, dto);

        // Vision ADD 이후 대시보드 자동 갱신
        publishMealChangedAfterCommit(userId);
        return foodName + "을(를) 추가 섭취로 기록했어요.";
    }

    @Override
    @Transactional
    public String applyVisionReplace(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        if (userId == null || date == null || analyzedFood == null || analyzedFood.isEmpty()) {
            return "이미지 분석 결과를 반영할 수 없어요. 다시 시도해주세요.";
        }

        Meal.MealTime mealTime = null;
        if (mealTimeOrNull != null && !mealTimeOrNull.isBlank()) {
            try {
                mealTime = Meal.MealTime.valueOf(mealTimeOrNull.trim().toUpperCase());
            } catch (Exception ignored) {
                mealTime = null;
            }
        }
        
        // 람다 표현식에서 사용하기 위해 final 변수로 복사
        final Meal.MealTime finalMealTime = mealTime;

        // 해당 끼니의 기존 PLANNED 식단 찾기
        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> {
                    if (finalMealTime != null && m.getMealTime() != finalMealTime) return false;
                    if (m.getStatus() != Meal.MealStatus.PLANNED) return false;
                    if (Boolean.TRUE.equals(m.getIsAdditional())) return false;
                    return true;
                })
                .toList();

        String foodName = String.valueOf(analyzedFood.getOrDefault("foodName", "알 수 없음"));
        Integer calories = getIntValue(analyzedFood.get("calories"));
        Integer carbs = getIntValue(analyzedFood.get("carbs"));
        Integer protein = getIntValue(analyzedFood.get("protein"));
        Integer fat = getIntValue(analyzedFood.get("fat"));

        if (existing.isEmpty()) {
            // 대체할 계획이 없으면 ADD로 fallback
            return applyVisionAdd(userId, date, mealTimeOrNull, analyzedFood);
        }

        // 첫 번째는 EATEN으로 교체, 나머지는 SKIPPED
        Meal first = existing.get(0);
        first.updateMealInfo(foodName, null, calories, carbs, protein, fat, Meal.MealStatus.EATEN);

        for (int i = 1; i < existing.size(); i++) {
            existing.get(i).changeStatus(Meal.MealStatus.SKIPPED);
        }

        // Vision REPLACE 이후 대시보드 자동 갱신
        publishMealChangedAfterCommit(userId);
        return foodName + "으로 " + (mealTime != null ? mealTime.getLabel() : "해당 끼니") + "를 대체했어요.";
    }

    @Override
    @Transactional
    public String toggleMealTimeComplete(Long userId, LocalDate date, String mealTime) {
        if (userId == null || date == null || mealTime == null || mealTime.isBlank()) {
            return "처리할 날짜/끼니 정보가 부족해요. (예: '오늘 점심 완료')";
        }
        Meal.MealTime mt;
        try {
            mt = Meal.MealTime.valueOf(mealTime.trim().toUpperCase());
        } catch (Exception e) {
            return "끼니를 이해하지 못했어요. (아침/점심/저녁 중 선택)";
        }

        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getMealTime() == mt)
                .toList();

        List<Meal> planned = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .toList();
        List<Meal> eatenNotAdditional = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN && (m.getIsAdditional() == null || !m.getIsAdditional()))
                .toList();

        if (!planned.isEmpty()) {
            planned.forEach(m -> m.changeStatus(Meal.MealStatus.EATEN));
            publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니를 완료 처리했어요.";
        }
        if (!eatenNotAdditional.isEmpty()) {
            eatenNotAdditional.forEach(m -> m.changeStatus(Meal.MealStatus.PLANNED));
            publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니 완료를 취소했어요.";
        }
        return mt.getLabel() + "에 완료/계획 처리할 항목이 없어요.";
    }

    @Override
    @Transactional
    public String toggleMealTimeSkip(Long userId, LocalDate date, String mealTime) {
        if (userId == null || date == null || mealTime == null || mealTime.isBlank()) {
            return "처리할 날짜/끼니 정보가 부족해요. (예: '오늘 점심 생략')";
        }
        Meal.MealTime mt;
        try {
            mt = Meal.MealTime.valueOf(mealTime.trim().toUpperCase());
        } catch (Exception e) {
            return "끼니를 이해하지 못했어요. (아침/점심/저녁 중 선택)";
        }

        // 재배분으로 생성된 "추가 메뉴(PLANNED, isAdditional=true)"는
        // - 생략 시점/취소 시점에 모두 정리하여 일관성을 유지합니다.
        List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> additionalPlannedToDelete = dayMeals.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsAdditional()))
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .toList();
        if (!additionalPlannedToDelete.isEmpty()) {
            mealRepository.deleteAll(additionalPlannedToDelete);
            mealRepository.flush();
        }

        List<Meal> meals = dayMeals.stream()
                .filter(m -> m.getMealTime() == mt)
                .toList();

        List<Meal> planned = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                .toList();
        List<Meal> skippedNotAdditional = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED && (m.getIsAdditional() == null || !m.getIsAdditional()))
                .toList();

        if (!planned.isEmpty()) {
            // [중요] 대체(Vision Replace) 같은 변칙 플로우에서 이미 SKIPPED로 남아있던 "이전 메뉴"가
            // 끼니 전체 생략 시점에 갑자기 다시 UI에 뜨는 문제가 있어,
            // 끼니 생략을 확정하는 순간 해당 끼니의 기존 SKIPPED(non-additional) 잔재는 정리합니다.
            List<Meal> staleSkipped = meals.stream()
                    .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED)
                    .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                    .toList();
            if (!staleSkipped.isEmpty()) {
                mealRepository.deleteAll(staleSkipped);
                mealRepository.flush();
            }

            planned.forEach(m -> m.changeStatus(Meal.MealStatus.SKIPPED));
            publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니를 생략 처리했어요.";
        }
        if (!skippedNotAdditional.isEmpty()) {
            skippedNotAdditional.forEach(m -> m.changeStatus(Meal.MealStatus.PLANNED));
            publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니 생략을 취소했어요.";
        }
        return mt.getLabel() + "에 생략/계획 처리할 항목이 없어요.";
    }

    @Override
    @Transactional
    public String toggleItemByFoodName(Long userId, LocalDate date, String mealTimeOrNull, String foodName, String mode) {
        if (userId == null || date == null || foodName == null || foodName.isBlank()) {
            return "처리할 음식명이 필요해요. (예: '오늘 점심 칼국수 생략')";
        }
        String target = foodName.trim();
        Meal.MealTime mt = null;
        if (mealTimeOrNull != null && !mealTimeOrNull.isBlank()) {
            try {
                mt = Meal.MealTime.valueOf(mealTimeOrNull.trim().toUpperCase());
            } catch (Exception ignored) {
                mt = null;
            }
        }
        final Meal.MealTime mtFinal = mt;

        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> mtFinal == null || m.getMealTime() == mtFinal)
                .toList();

        List<Meal> candidates = meals.stream()
                .filter(m -> m.getFoodName() != null && m.getFoodName().toLowerCase().contains(target.toLowerCase()))
                .toList();

        if (candidates.isEmpty()) {
            return "해당 음식('" + target + "')을(를) 찾지 못했어요. 음식명을 조금 더 정확히 말해줘요.";
        }
        if (candidates.size() > 1) {
            return "같은 이름의 항목이 여러 개 있어요. 끼니(아침/점심/저녁)까지 함께 말해줘요.";
        }

        Meal m = candidates.get(0);
        String mMode = mode == null ? "COMPLETE" : mode.trim().toUpperCase();
        if ("SKIP".equals(mMode)) {
            Meal.MealStatus next = (m.getStatus() == Meal.MealStatus.SKIPPED) ? Meal.MealStatus.PLANNED : Meal.MealStatus.SKIPPED;
            m.changeStatus(next);
            publishMealChangedAfterCommit(userId);
            return "'" + m.getFoodName() + "' 항목을 " + (next == Meal.MealStatus.SKIPPED ? "생략" : "생략취소") + " 처리했어요.";
        }
        // COMPLETE
        Meal.MealStatus next = (m.getStatus() == Meal.MealStatus.EATEN) ? Meal.MealStatus.PLANNED : Meal.MealStatus.EATEN;
        m.changeStatus(next);
        publishMealChangedAfterCommit(userId);
        return "'" + m.getFoodName() + "' 항목을 " + (next == Meal.MealStatus.EATEN ? "완료" : "완료취소") + " 처리했어요.";
    }

    private Integer getIntValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}