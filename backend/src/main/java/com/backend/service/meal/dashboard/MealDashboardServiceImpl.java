package com.backend.service.meal.dashboard;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealDashboardDto;
import com.backend.dto.meal.MealDto;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.target.MealTargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealDashboardServiceImpl implements MealDashboardService {

    private final MealSearch mealSearch;
    private final MealTargetService mealTargetService;

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

        // 3. [식단 변동 내역] 끼니별 최신 1개만 표시 (changedAt 기준)
        dashboardDto.setAnalysisComments(buildLatestReplaceComments(meals));

        log.info("[Dashboard] 조립 완료");
        return dashboardDto;
    }

    /**
     * 끼니별 최신 REPLACE 변동 1건만 summary로 내려줍니다.
     *
     * 프론트 파서 호환 포맷:
     * - "변동: [점심] A, B -> [점심] X"
     */
    private List<String> buildLatestReplaceComments(List<Meal> meals) {
        if (meals == null || meals.isEmpty()) return new ArrayList<>();

        List<Meal.MealTime> times = List.of(Meal.MealTime.BREAKFAST, Meal.MealTime.LUNCH, Meal.MealTime.DINNER);
        List<String> out = new ArrayList<>();

        for (Meal.MealTime mt : times) {
            Optional<java.time.Instant> latestOpt = meals.stream()
                    .filter(m -> m != null && m.getMealTime() == mt)
                    .map(Meal::getChangedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());

            if (latestOpt.isEmpty()) continue;
            java.time.Instant latest = latestOpt.get();

            List<Meal> group = meals.stream()
                    .filter(m -> m != null && m.getMealTime() == mt)
                    .filter(m -> Objects.equals(m.getChangedAt(), latest))
                    .filter(m -> m.getChanged() == Meal.MealChanged.REPLACED_OUT || m.getChanged() == Meal.MealChanged.REPLACED_IN)
                    .toList();

            if (group.isEmpty()) continue;

            List<String> before = group.stream()
                    .filter(m -> m.getChanged() == Meal.MealChanged.REPLACED_OUT)
                    .map(Meal::getFoodName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();

            List<String> after = group.stream()
                    .filter(m -> m.getChanged() == Meal.MealChanged.REPLACED_IN)
                    .map(Meal::getFoodName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();

            if (after.isEmpty()) continue;

            String beforeText = before.isEmpty() ? "-" : String.join(", ", before);
            String afterText = String.join(", ", after);
            out.add("변동: [" + mt.getLabel() + "] " + beforeText + " -> [" + mt.getLabel() + "] " + afterText);
        }
        return out;
    }
}


