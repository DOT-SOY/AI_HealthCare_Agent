package com.backend.service.meal.plan;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealDto;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.ws.MealWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealPlanServiceImpl implements MealPlanService {

    private final MealRepository mealRepository;
    private final MealSearch mealSearch;
    private final MealWsPublisher mealWsPublisher;

    @Override
    @Transactional
    public void updatePlannedMeals(Long userId, LocalDate date, List<MealDto> newPlans) {
        log.info("[MealPlan] updatePlannedMeals - User: {}, Date: {}", userId, date);

        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);

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

        List<Meal> toDelete = existing.stream()
                .filter(m -> timesInNewPlan.isEmpty() || timesInNewPlan.contains(m.getMealTime()))
                .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                .filter(m -> m.getStatus() != Meal.MealStatus.EATEN)
                .toList();

        if (!toDelete.isEmpty()) {
            mealRepository.deleteAll(toDelete);
            mealRepository.flush();
        }

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
            mealRepository.save(dto.toEntity(userId));
        }

        mealWsPublisher.publishMealChangedAfterCommit(userId);
        log.info("[MealPlan] updatePlannedMeals done - delete={}, insert={}", toDelete.size(), newPlans != null ? newPlans.size() : 0);
    }

    @Override
    @Transactional
    public void overwritePlannedMealsKeepEaten(Long userId, LocalDate date, List<MealDto> newPlans) {
        log.info("[MealPlan] overwritePlannedMealsKeepEaten - User: {}, Date: {}", userId, date);

        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> toDelete = existing.stream()
                .filter(m -> m.getStatus() != Meal.MealStatus.EATEN)
                .toList();

        if (!toDelete.isEmpty()) {
            mealRepository.deleteAll(toDelete);
            mealRepository.flush();
        }

        for (MealDto dto : newPlans) {
            if (dto.getMealDate() == null) {
                dto.setMealDate(date);
            }
            dto.setStatus(Meal.MealStatus.PLANNED.name());
            dto.setIsAdditional(false); // 덮어쓰기 생성은 추가메뉴 여부를 따지지 않음(새 계획은 항상 non-additional)
            mealRepository.save(dto.toEntity(userId));
        }

        mealWsPublisher.publishMealChangedAfterCommit(userId);
        log.info("[MealPlan] overwrite done - delete={}, insert={}", toDelete.size(), newPlans != null ? newPlans.size() : 0);
    }
}


