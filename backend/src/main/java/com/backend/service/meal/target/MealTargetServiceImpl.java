package com.backend.service.meal.target;

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
        try {
            // 1. 월간 식단 데이터 원샷 조회 (QueryDSL)
            List<MealCalendarDto> monthlySums = mealSearch.findMonthlyMealSummary(userId, yearMonth);
            if (monthlySums == null) {
                monthlySums = new ArrayList<>();
            }
            
            // 2. 월간 목표 설정 데이터 원샷 조회
            LocalDate start = yearMonth.withDayOfMonth(1);
            LocalDate end = yearMonth.withDayOfMonth(yearMonth.lengthOfMonth());
            List<MealTarget> monthTargets = targetSearch.findTargetsBetweenDates(userId, start, end);
            Map<LocalDate, MealTarget> targetMap = monthTargets != null 
                ? monthTargets.stream()
                    .filter(t -> t != null && t.getTargetDate() != null)
                    .collect(Collectors.toMap(MealTarget::getTargetDate, t -> t, (existing, replacement) -> existing))
                : new HashMap<>();

            // 3. 목표 승계 기초 데이터 (이번 달 첫 날 이전의 최신 목표)
            MealTarget lastActiveTarget = targetSearch.findLatestTargetBeforeDate(userId, start);

            // 4. 전수 조사 및 오차 판정 (±10%, ±2%)
            for (MealCalendarDto dayDto : monthlySums) {
                if (dayDto == null || dayDto.getMealDate() == null) {
                    continue;
                }
                
                if (targetMap.containsKey(dayDto.getMealDate())) {
                    lastActiveTarget = targetMap.get(dayDto.getMealDate());
                }

                if (lastActiveTarget != null) {
                    try {
                        applyAchievementLogic(dayDto, lastActiveTarget);
                    } catch (Exception e) {
                        log.warn("성취도 계산 실패 - 날짜: {}, 에러: {}", dayDto.getMealDate(), e.getMessage());
                    }
                }
            }
            return monthlySums;
        } catch (Exception e) {
            log.error("월간 캘린더 상태 조회 실패 - userId: {}, yearMonth: {}, 에러: {}", userId, yearMonth, e.getMessage(), e);
            return new ArrayList<>(); // 에러 발생 시 빈 리스트 반환
        }
    }

    /**
     * [상세 대시보드용] 오늘 목표 대비 섭취 현황 및 끼니별 섹션 데이터 조립
     */
    @Override
    public void getNutritionAchievement(Long userId, LocalDate date, MealDashboardDto dashboardDto) {
        MealTargetDto target = getTargetByDate(userId, date);
        if (target == null) return;

        dashboardDto.setDayTarget(target);
        dashboardDto.setAiAnalysis(target.getAiFeedback());

        // 해당 날짜의 모든 식단 데이터 로드 (정렬됨)
        List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);

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
        // "끼니 전체 생략" 판정:
        // - non-additional 기준으로 PLANNED/EATEN이 없고, SKIPPED가 존재할 때만 true
        // - (예: Vision 대체로 일부만 SKIPPED 처리된 경우는 "끼니 전체 생략"이 아님)
        List<Meal> timeMeals = meals.stream()
                .filter(m -> m.getMealTime() == time)
                .toList();

        boolean hasNonAdditionalSkipped = timeMeals.stream()
                .anyMatch(m -> m.getStatus() == Meal.MealStatus.SKIPPED
                        && (m.getIsAdditional() == null || !m.getIsAdditional())
                        && m.getChanged() != Meal.MealChanged.REPLACED_OUT);
        boolean hasNonAdditionalPlanned = timeMeals.stream()
                .anyMatch(m -> m.getStatus() == Meal.MealStatus.PLANNED && (m.getIsAdditional() == null || !m.getIsAdditional()));

        // [중요] 추가 섭취(isAdditional=true)로 EATEN이 들어오면, 끼니 전체를 "생략"으로 취급하면 UX가 깨집니다.
        // 예: 끼니를 생략한 뒤, 사진으로 "추가 섭취 기록"을 하면 해당 끼니가 계속 0kcal/생략 상태로 보임.
        // 정책: 해당 끼니에 EATEN(추가/정규 무관)이 1개라도 있으면 "끼니 전체 생략"으로 보지 않습니다.
        boolean hasAnyEaten = timeMeals.stream()
                .anyMatch(m -> m.getStatus() == Meal.MealStatus.EATEN);

        boolean mealTimeSkipped = hasNonAdditionalSkipped && !hasNonAdditionalPlanned && !hasAnyEaten;

        // UI 정책:
        // - "사용자 메뉴별 생략"은 리스트에서 사라지면 취소가 불가하므로, SKIPPED라도 화면에 유지해야 합니다.
        // - 단, 교체(VISION REPLACE)로 밀려난 잔여 항목은 changed=REPLACED_OUT로 마킹되어 화면에서 숨깁니다.
        // - 합계(칼로리/탄단지)는 SKIPPED 항목을 제외하고 계산합니다.
        List<Meal> visibleMeals = timeMeals.stream()
                .filter(m -> m.getChanged() != Meal.MealChanged.REPLACED_OUT)
                .filter(m -> {
                    if (mealTimeSkipped) {
                        // 끼니 전체 생략 상태면, non-additional 메뉴만 노출(회색 처리용)
                        return (m.getIsAdditional() == null || !m.getIsAdditional());
                    }
                    return true;
                })
                .toList();

        List<MealDto> sectionMeals = visibleMeals.stream()
                .map(MealDto::fromEntity)
                .collect(Collectors.toList());

        int sCal = mealTimeSkipped ? 0 : visibleMeals.stream()
                .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                .mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0)
                .sum();
        int sCarb = mealTimeSkipped ? 0 : visibleMeals.stream()
                .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                .mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0)
                .sum();
        int sProt = mealTimeSkipped ? 0 : visibleMeals.stream()
                .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                .mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0)
                .sum();
        int sFat = mealTimeSkipped ? 0 : visibleMeals.stream()
                .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                .mapToInt(m -> m.getFat() != null ? m.getFat() : 0)
                .sum();

        return MealDashboardDto.MealTimeSection.builder()
                .skipped(mealTimeSkipped)
                .totalCalories(sCal)
                .totalCarbs(sCarb).totalProtein(sProt).totalFat(sFat)
                // 하루 전체 목표량 중 이 끼니가 차지하는 비중 계산
                .percentCarbs(calcRatio(target.getGoalCarbs(), sCarb))
                .percentProtein(calcRatio(target.getGoalProtein(), sProt))
                .percentFat(calcRatio(target.getGoalFat(), sFat))
                .meals(sectionMeals)
                .build();
    }

    /**
     * [핵심 로직] ±10% / ±2% 판정 알고리즘
     */
    private void applyAchievementLogic(MealCalendarDto dto, MealTarget target) {
        if (dto == null || target == null) {
            return;
        }
        
        Integer totalEatenCalories = dto.getTotalEatenCalories();
        Integer goalCal = target.getGoalCal();
        
        // null 체크
        if (totalEatenCalories == null) {
            totalEatenCalories = 0;
        }
        if (goalCal == null || goalCal == 0) {
            dto.setGoalCalories(0);
            dto.setAchievementRate(0);
            dto.setDailyStatus("FAIL");
            dto.setIsSuccess(false);
            return;
        }
        
        int percent = (int) ((totalEatenCalories / (double) goalCal) * 100);
        double diff = Math.abs(100 - percent);

        dto.setGoalCalories(goalCal);
        dto.setAchievementRate(percent);
        
        // 엔터프라이즈급 상태 판정
        String status = (diff <= 2.0) ? "PERFECT" : (diff <= 10.0) ? "PASS" : "FAIL";
        dto.setDailyStatus(status);
        dto.setIsSuccess(!status.equals("FAIL"));
        
        // 탄단지 각각의 O/X 체크 로직도 여기서 수행 (UI 그림 반영)
        // ... (생략된 상세 탄단지 체크 로직)
    }

    private MealDashboardDto.NutritionSummary calculateSummary(Integer goal, int current) {
        int percent = (goal == null || goal == 0) ? 0 : (int)((current / (double)goal) * 100);
        double diff = Math.abs(100 - percent);
        String status = (diff <= 2.0) ? "PERFECT" : (diff <= 10.0) ? "PASS" : "FAIL";
        return MealDashboardDto.NutritionSummary.builder().goal(goal).current(current).percent(percent).status(status).build();
    }

    private int sum(List<Meal> meals, String type) {
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
        MealTarget target = targetRepository.findByUserIdAndTargetDate(userId, date)
                .orElseGet(() -> targetSearch.findLatestTargetBeforeDate(userId, date));
        return target != null ? MealTargetDto.fromEntity(target) : null;
    }

    @Override @Transactional
    public MealTargetDto updateTarget(Long userId, MealTargetDto dto) {
        MealTarget target = targetRepository.findByUserIdAndTargetDate(userId, dto.getTargetDate())
                .orElseGet(() -> dto.toEntity(userId));
        // MemberInfoBody.ExercisePurpose와 값(DIET, MAINTAIN, BULK_UP)을 공유
        target.updateTarget(
                dto.getGoalType() != null
                        ? com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose.valueOf(dto.getGoalType())
                        : null,
                dto.getGoalCal(), dto.getGoalCarbs(), dto.getGoalProtein(), dto.getGoalFat());
        return MealTargetDto.fromEntity(targetRepository.save(target));
    }

    @Override @Transactional
    public void updateAiFeedback(Long userId, LocalDate date, String feedback) {
        targetRepository.findByUserIdAndTargetDate(userId, date).ifPresent(t -> t.updateFeedback(feedback));
    }
    
    @Override
    public MealTargetDto calculateRemainingNutrients(Long userId, LocalDate date) {
        // 1. 목표 조회
        MealTargetDto target = getTargetByDate(userId, date);
        if (target == null) return null; // 목표 없으면 계산 불가

        // 2. 현재까지 먹은 양 조회 (EATEN만)
        List<Meal> eatenMeals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN)
                .toList();

        int currentCal = eatenMeals.stream().mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0).sum();
        int currentCarb = eatenMeals.stream().mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0).sum();
        int currentProt = eatenMeals.stream().mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0).sum();
        int currentFat = eatenMeals.stream().mapToInt(m -> m.getFat() != null ? m.getFat() : 0).sum();

        // 3. 생략된 "끼니 전체"의 영양성분 합산
        // - 같은 끼니 안에서 일부 항목만 SKIPPED(예: 대체 과정에서 나머지 항목 SKIPPED)는 재분배 대상이 아님
        // - 끼니 전체 생략은: 해당 mealTime에 non-additional EATEN이 없고, non-additional SKIPPED가 존재하는 경우로 판단
        List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                .toList();

        java.util.Set<Meal.MealTime> eatenTimes = dayMeals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN)
                .map(Meal::getMealTime)
                .collect(java.util.stream.Collectors.toSet());

        List<Meal> skippedMeals = dayMeals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED)
                .filter(m -> m.getChanged() != Meal.MealChanged.REPLACED_OUT)
                .filter(m -> m.getMealTime() != null && !eatenTimes.contains(m.getMealTime()))
                .toList();

        int skippedCal = skippedMeals.stream().mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0).sum();
        int skippedCarb = skippedMeals.stream().mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0).sum();
        int skippedProt = skippedMeals.stream().mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0).sum();
        int skippedFat = skippedMeals.stream().mapToInt(m -> m.getFat() != null ? m.getFat() : 0).sum();

        // 4. 잔여량 계산: 목표 - (먹은 양) + (생략된 끼니의 영양성분)
        // 생략된 끼니의 영양성분을 남은 끼니에 재분배하기 위해 잔여량에 추가
        return MealTargetDto.builder()
                .goalCal(Math.max(0, target.getGoalCal() - currentCal + skippedCal))
                .goalCarbs(Math.max(0, target.getGoalCarbs() - currentCarb + skippedCarb))
                .goalProtein(Math.max(0, target.getGoalProtein() - currentProt + skippedProt))
                .goalFat(Math.max(0, target.getGoalFat() - currentFat + skippedFat))
                .build();
    }
}