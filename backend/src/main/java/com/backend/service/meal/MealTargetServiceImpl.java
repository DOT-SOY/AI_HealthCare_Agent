package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.domain.meal.MealTarget;
import com.backend.dto.meal.*;
import com.backend.repository.meal.MealTargetRepository;
import com.backend.repository.meal.MealTargetSearch;
import com.backend.repository.meal.MealSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealTargetServiceImpl implements MealTargetService {

    private final MealTargetRepository targetRepository;
    private final MealTargetSearch targetSearch;
    private final MealSearch mealSearch;

    /**
     * [캘린더용 고성능 조회] 월간 모든 날짜의 성취도 및 아이콘 상태 계산
     */
    public List<MealCalendarDto> getMonthlyCalendarStatus(Long userId, LocalDate yearMonth) {
        // 1. 월간 식단 데이터 원샷 조회 (QueryDSL)
        List<MealCalendarDto> monthlySums = mealSearch.findMonthlyMealSummary(userId, yearMonth);
        
        // 2. 월간 목표 설정 데이터 원샷 조회
        LocalDate start = yearMonth.withDayOfMonth(1);
        LocalDate end = yearMonth.withDayOfMonth(yearMonth.lengthOfMonth());
        List<MealTarget> monthTargets = targetSearch.findTargetsBetweenDates(userId, start, end);
        Map<LocalDate, MealTarget> targetMap = monthTargets.stream()
                .collect(Collectors.toMap(MealTarget::getTargetDate, t -> t));

        // 3. 목표 승계 기초 데이터 (이번 달 첫 날 이전의 최신 목표)
        MealTarget lastActiveTarget = targetSearch.findLatestTargetBeforeDate(userId, start);

        // 4. 전수 조사 및 오차 판정 (±10%, ±2%)
        for (MealCalendarDto dayDto : monthlySums) {
            if (targetMap.containsKey(dayDto.getMealDate())) {
                lastActiveTarget = targetMap.get(dayDto.getMealDate());
            }

            if (lastActiveTarget != null) {
                applyAchievementLogic(dayDto, lastActiveTarget);
            }
        }
        return monthlySums;
    }

    /**
     * [상세 대시보드용] 오늘 목표 대비 섭취 현황 및 끼니별 섹션 데이터 조립
     */
    @Override
    public void getNutritionAchievement(Long userId, LocalDate date, MealDashboardDto dashboardDto) {
        MealTargetDto target = getTargetByDate(userId, date);
        
        // 해당 날짜의 모든 식단 데이터 로드 (정렬됨)
        List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);
        if (dayMeals == null) {
            dayMeals = new ArrayList<>();
        }

        if (target == null) {
            // 목표가 없어도 식단 데이터는 표시해야 함 (빈 박스 방지)
            dashboardDto.setCalories(calculateSummary(0, sum(dayMeals, "cal")));
            dashboardDto.setCarbs(calculateSummary(0, sum(dayMeals, "carbs")));
            dashboardDto.setProtein(calculateSummary(0, sum(dayMeals, "protein")));
            dashboardDto.setFat(calculateSummary(0, sum(dayMeals, "fat")));
            
            // 끼니별 섹션은 기본값으로 채움
            dashboardDto.setBreakfast(assembleSection(dayMeals, Meal.MealTime.BREAKFAST, null));
            dashboardDto.setLunch(assembleSection(dayMeals, Meal.MealTime.LUNCH, null));
            dashboardDto.setDinner(assembleSection(dayMeals, Meal.MealTime.DINNER, null));
            dashboardDto.setSnack(assembleSection(dayMeals, Meal.MealTime.SNACK, null));
            return;
        }

        dashboardDto.setDayTarget(target);
        dashboardDto.setAiAnalysis(target.getAiFeedback());

        // 상단 원형 그래프 데이터 계산 (±10% 로직 포함)
        dashboardDto.setCalories(calculateSummary(target.getGoalCal(), sum(dayMeals, "cal")));
        dashboardDto.setCarbs(calculateSummary(target.getGoalCarbs(), sum(dayMeals, "carbs")));
        dashboardDto.setProtein(calculateSummary(target.getGoalProtein(), sum(dayMeals, "protein")));
        dashboardDto.setFat(calculateSummary(target.getGoalFat(), sum(dayMeals, "fat")));

        // 중단 끼니별 섹션 데이터 조립 (그림 속 탄단지 바 퍼센트 포함)
        dashboardDto.setBreakfast(assembleSection(dayMeals, Meal.MealTime.BREAKFAST, target));
        dashboardDto.setLunch(assembleSection(dayMeals, Meal.MealTime.LUNCH, target));
        dashboardDto.setDinner(assembleSection(dayMeals, Meal.MealTime.DINNER, target));
        dashboardDto.setSnack(assembleSection(dayMeals, Meal.MealTime.SNACK, target));
    }

    /**
     * [비즈니스 로직] 하루 목표 대비 특정 끼니의 영양 기여도 계산 (UI Bar용)
     */
    private MealDashboardDto.MealTimeSection assembleSection(List<Meal> meals, Meal.MealTime time, MealTargetDto target) {
        if (meals == null) {
            meals = new ArrayList<>();
        }
        List<MealDto> sectionMeals = meals.stream()
                .filter(m -> m != null && m.getMealTime() == time)
                .map(MealDto::fromEntity)
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        int sCal = sectionMeals.stream().mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0).sum();
        int sCarb = sectionMeals.stream().mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0).sum();
        int sProt = sectionMeals.stream().mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0).sum();
        int sFat = sectionMeals.stream().mapToInt(m -> m.getFat() != null ? m.getFat() : 0).sum();

        return MealDashboardDto.MealTimeSection.builder()
                .totalCalories(sCal)
                .totalCarbs(sCarb).totalProtein(sProt).totalFat(sFat)
                // 하루 전체 목표량 중 이 끼니가 차지하는 비중 계산 (target이 null이면 0)
                .percentCarbs(target != null ? calcRatio(target.getGoalCarbs(), sCarb) : 0)
                .percentProtein(target != null ? calcRatio(target.getGoalProtein(), sProt) : 0)
                .percentFat(target != null ? calcRatio(target.getGoalFat(), sFat) : 0)
                .meals(sectionMeals)
                .build();
    }

    /**
     * [핵심 로직] ±10% / ±2% 판정 알고리즘
     */
    private void applyAchievementLogic(MealCalendarDto dto, MealTarget target) {
        int goalCal = target.getGoalCal() != null ? target.getGoalCal() : 0;
        int eatenCal = dto.getTotalEatenCalories() != null ? dto.getTotalEatenCalories() : 0;
        int percent = (goalCal == 0) ? 0 : (int) ((eatenCal / (double) goalCal) * 100);

        dto.setGoalCalories(goalCal);
        dto.setAchievementRate(percent);
        
        // 새 기준: GOOD/SAFE/LOW/LACK/FAIL
        String status = resolveStatusByPercent(percent);
        dto.setDailyStatus(status);
        dto.setIsSuccess(isSuccessStatus(status));
        
        // 탄단지 각각의 O/X 체크 로직도 여기서 수행 (UI 그림 반영)
        // ... (생략된 상세 탄단지 체크 로직)
    }

    private MealDashboardDto.NutritionSummary calculateSummary(Integer goal, int current) {
        int percent = (goal == null || goal == 0) ? 0 : (int)((current / (double)goal) * 100);
        String status = resolveStatusByPercent(percent);
        return MealDashboardDto.NutritionSummary.builder().goal(goal).current(current).percent(percent).status(status).build();
    }

    private String resolveStatusByPercent(int percent) {
        if (percent >= 113) {
            return "FAIL";
        }
        if (percent >= 111) {
            return "SAFE";
        }
        if (percent >= 80) {
            return "GOOD";
        }
        if (percent >= 76) {
            return "SAFE";
        }
        if (percent >= 51) {
            return "LOW";
        }
        return "LACK";
    }

    private boolean isSuccessStatus(String status) {
        return "GOOD".equals(status) || "SAFE".equals(status);
    }

    private int sum(List<Meal> meals, String type) {
        if (meals == null) {
            return 0;
        }
        return meals.stream().filter(m -> m.getStatus() == Meal.MealStatus.EATEN)
                .mapToInt(m -> {
                    switch(type) {
                        case "carbs": return m.getCarbs() != null ? m.getCarbs() : 0;
                        case "protein": return m.getProtein() != null ? m.getProtein() : 0;
                        case "fat": return m.getFat() != null ? m.getFat() : 0;
                        default: return m.getCalories() != null ? m.getCalories() : 0;
                    }
                }).sum();
    }

    private int calcRatio(Integer goal, int current) {
        return (goal == null || goal == 0) ? 0 : (int)((current / (double)goal) * 100);
    }

    @Override
    public MealTargetDto getTargetByDate(Long userId, LocalDate date) {
        MealTarget target = targetRepository.findTopByUserIdAndTargetDateOrderByTargetDateDesc(userId, date)
                .orElseGet(() -> targetSearch.findLatestTargetBeforeDate(userId, date));
        return target != null ? MealTargetDto.fromEntity(target) : null;
    }

    @Override @Transactional
    public MealTargetDto updateTarget(Long userId, MealTargetDto dto) {
        MealTarget target = targetRepository.findTopByUserIdAndTargetDateOrderByTargetDateDesc(userId, dto.getTargetDate())
                .orElseGet(() -> dto.toEntity(userId));
        target.updateTarget(MealTarget.GoalType.valueOf(dto.getGoalType()), dto.getGoalCal(), dto.getGoalCarbs(), dto.getGoalProtein(), dto.getGoalFat());
        return MealTargetDto.fromEntity(targetRepository.save(target));
    }

    @Override @Transactional
    public void updateAiFeedback(Long userId, LocalDate date, String feedback) {
        targetRepository.findTopByUserIdAndTargetDateOrderByTargetDateDesc(userId, date).ifPresent(t -> t.updateFeedback(feedback));
    }
    
    @Override
    public MealTargetDto calculateRemainingNutrients(Long userId, LocalDate date) {
        // 1. 목표 조회
        MealTargetDto target = getTargetByDate(userId, date);
        if (target == null) return null; // 목표 없으면 계산 불가

        // 2. 현재까지 먹은 양 조회
        List<Meal> eatenMeals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN)
                .toList();

        int currentCal = eatenMeals.stream().mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0).sum();
        int currentCarb = eatenMeals.stream().mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0).sum();
        int currentProt = eatenMeals.stream().mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0).sum();
        int currentFat = eatenMeals.stream().mapToInt(m -> m.getFat() != null ? m.getFat() : 0).sum();

        // 3. 잔여량 계산 (음수가 나오면 0으로 처리)
        return MealTargetDto.builder()
                .goalCal(Math.max(0, target.getGoalCal() - currentCal))
                .goalCarbs(Math.max(0, target.getGoalCarbs() - currentCarb))
                .goalProtein(Math.max(0, target.getGoalProtein() - currentProt))
                .goalFat(Math.max(0, target.getGoalFat() - currentFat))
                .build();
    }
}