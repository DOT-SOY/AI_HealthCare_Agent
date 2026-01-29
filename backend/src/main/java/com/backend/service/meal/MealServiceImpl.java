package com.backend.service.meal;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.*;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
public class MealServiceImpl implements MealService {

    private final MealRepository mealRepository;
    private final MealSearch mealSearch;
    private final MealTargetService mealTargetService;
    private final AiMealClient aiMealClient;
    private final SimpMessagingTemplate messagingTemplate;

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

        // 3. [핵심] 자바 기반 식단 변동 리포트 생성 (Intelligence)
        List<String> analysisReport = generateDetailedAnalysisReport(meals);
        dashboardDto.setAnalysisComments(analysisReport);

        log.info("[Dashboard] 조립 완료 - 생성된 분석 멘트: {}개", analysisReport.size());
        return dashboardDto;
    }

    /**
     * [변동 분석 엔진] 
     * 계획(Original)과 실측(Current) 데이터를 전수 비교하여 사람이 말하는 듯한 메시지를 생성합니다.
     */
    private List<String> generateDetailedAnalysisReport(List<Meal> meals) {
        List<String> report = new ArrayList<>();

        for (Meal m : meals) {
            String timeLabel = m.getMealTime().getLabel(); // 아침, 점심 등

            // [상황 1] 계획에 없던 추가 섭취 (Add-on)
            if (m.getIsAdditional()) {
                report.add(String.format("▶ [%s] 계획에 없던 '%s'을(를) 추가로 섭취하셨습니다.", 
                        timeLabel, m.getFoodName()));
                continue;
            }

            // [상황 2] 예정된 식사 건너뛰기 (SKIPPED)
            if (m.getStatus() == Meal.MealStatus.SKIPPED) {
                report.add(String.format("▷ [%s] 원래 드시기로 했던 '%s' 식사를 거르셨습니다.", 
                        timeLabel, m.getOriginalFoodName()));
                continue;
            }

            // [상황 3] 메뉴 변경 및 영양소 오차 분석
            if (m.getOriginalFoodName() != null && !m.getOriginalFoodName().equals(m.getFoodName())) {
                report.add(String.format("● [%s] 식단이 변경되었습니다: [%s] → [%s]", 
                        timeLabel, m.getOriginalFoodName(), m.getFoodName()));
                
                // 영양소 차이 정밀 계산 (Calorie, Carbs, Protein, Fat)
                analyzeNutrientDifference(report, m);
            }
        }

        if (report.isEmpty()) {
            report.add("오늘의 모든 식단 계획을 완벽하게 실천 중이시네요! 아주 훌륭합니다.");
        }
        return report;
    }

    /**
     * 메뉴 변경 시 세부 영양소 차이값을 문장으로 변환합니다.
     */
    private void analyzeNutrientDifference(List<String> report, Meal m) {
        int diffCal = (m.getCalories() != null ? m.getCalories() : 0) - (m.getOriginalCalories() != null ? m.getOriginalCalories() : 0);
        int diffProt = (m.getProtein() != null ? m.getProtein() : 0) - (m.getOriginalProtein() != null ? m.getOriginalProtein() : 0);

        if (Math.abs(diffCal) >= 10) {
            String calTrend = diffCal > 0 ? "더 많이" : "더 적게";
            report.add(String.format("   ㄴ 기존 계획보다 칼로리를 %dkcal %s 섭취하셨습니다.", Math.abs(diffCal), calTrend));
        }
        
        if (Math.abs(diffProt) >= 5) {
            String protTrend = diffProt > 0 ? "추가 확보" : "부족하게 섭취";
            report.add(String.format("   ㄴ 단백질은 계획 대비 %dg %s하셨습니다.", Math.abs(diffProt), protTrend));
        }
    }

    @Override
    @Transactional
    public MealDto registerAdditionalMeal(Long userId, MealDto mealDto) {
        log.info("[Meal] 추가 식단 등록 - User: {}, Food: {}", userId, mealDto.getFoodName());
        mealDto.setIsAdditional(true);
        mealDto.setStatus(Meal.MealStatus.EATEN.name());
        Meal saved = mealRepository.save(mealDto.toEntity(userId));
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
        return MealDto.fromEntity(meal);
    }

    @Override
    @Transactional
    public void toggleMealStatus(Long scheduleId, String status) {
        mealRepository.findById(scheduleId).ifPresent(m -> {
            log.info("[Meal] 상태 변경 - ID: {}, Status: {}", scheduleId, status);
            m.changeStatus(Meal.MealStatus.valueOf(status));
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
                    // WebSocket 전역 경로 푸시: /topic/meal/vision/{userId}
                    messagingTemplate.convertAndSend("/topic/meal/vision/" + userId, response.getAnalyzedFood());
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
        log.info("[Async] 식단 재구성(Replan) 시작 - User: {}", userId);
        
        // 잔여 영양소 계산 (목표 - 현재 섭취량)
        MealTargetDto remaining = mealTargetService.getTargetByDate(userId, date); 

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("REPLAN")
                .goal(AiMealRequestDto.GoalSpec.builder()
                        .targetCalories(remaining.getGoalCal())
                        .targetCarbs(remaining.getGoalCarbs())
                        .targetProtein(remaining.getGoalProtein())
                        .targetFat(remaining.getGoalFat())
                        .build())
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    // PLANNED 상태의 계획만 교체
                    // updatePlannedMeals()에 이미 @Transactional 있음
                    updatePlannedMeals(userId, date, response.getSuggestedMeals());

                    messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "남은 일정이 최적으로 재구성되었습니다.");
                    log.info("[Async] 식단 재구성 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] 식단 재구성 실패: ", throwable);
                    return null;
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
        
        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> toDelete = existing.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .collect(Collectors.toList());
        
        mealRepository.deleteAll(toDelete);
        
        // AI가 제안한 새로운 계획들을 저장
        for (MealDto dto : newPlans) {
            mealRepository.save(dto.toEntity(userId));
        }
        
        log.info("[Meal] 식단 계획 업데이트 완료 - 삭제: {}개, 추가: {}개", toDelete.size(), newPlans.size());
    }

    @Override
    @Transactional
    public void generateInitialPlan(Long userId, LocalDate date) {
        log.info("[Meal] 최초 식단 생성 시작 - User: {}", userId);
        // AI 생성 로직 호출 및 저장 (생략된 상세 로직 구현부)
    }
}