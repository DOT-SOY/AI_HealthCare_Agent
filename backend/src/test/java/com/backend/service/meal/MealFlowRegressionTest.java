package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.domain.meal.MealTarget;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.meal.MealDashboardDto;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealTargetRepository;
import com.backend.service.meal.target.MealTargetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class MealFlowRegressionTest {

    @Autowired
    private MealService mealService;

    @Autowired
    private MealTargetService mealTargetService;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private MealTargetRepository mealTargetRepository;

    @Test
    void visionReplace_thenUncomplete_thenMealTimeSkip_shouldNotResurrectOldSkippedMenus() {
        Long userId = 1L;
        LocalDate date = LocalDate.of(2026, 2, 5);

        // Given: lunch has 3 planned items
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.PLANNED)
                .isAdditional(false)
                .foodName("A")
                .calories(100).carbs(10).protein(10).fat(5)
                .originalFoodName("A").originalCalories(100).originalCarbs(10).originalProtein(10).originalFat(5)
                .build());
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.PLANNED)
                .isAdditional(false)
                .foodName("B")
                .calories(200).carbs(20).protein(20).fat(10)
                .originalFoodName("B").originalCalories(200).originalCarbs(20).originalProtein(20).originalFat(10)
                .build());
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.PLANNED)
                .isAdditional(false)
                .foodName("C")
                .calories(300).carbs(30).protein(30).fat(15)
                .originalFoodName("C").originalCalories(300).originalCarbs(30).originalProtein(30).originalFat(15)
                .build());

        // When: vision replace (first item becomes EATEN with X; others become SKIPPED)
        Map<String, Object> analyzedFood = new HashMap<>();
        analyzedFood.put("foodName", "X");
        analyzedFood.put("calories", 555);
        analyzedFood.put("carbs", 55);
        analyzedFood.put("protein", 55);
        analyzedFood.put("fat", 22);
        mealService.applyVisionReplace(userId, date, "LUNCH", analyzedFood);

        // And: user un-completes the mealTime (EATEN non-additional -> PLANNED)
        mealService.toggleMealTimeComplete(userId, date, "LUNCH");

        // And: user skips the whole mealTime (should delete stale SKIPPED remnants first)
        mealService.toggleMealTimeSkip(userId, date, "LUNCH");

        // Then: only the current planned item should remain as SKIPPED; old replaced-out menus shouldn't reappear
        List<Meal> lunchMeals = mealRepository.findByUserIdAndMealDate(userId, date).stream()
                .filter(m -> m.getMealTime() == Meal.MealTime.LUNCH)
                .toList();

        // active(노출) 항목은 1개(X)만 남아야 하고, 나머지는 REPLACED_OUT으로 숨김 처리되어야 한다
        List<Meal> visible = lunchMeals.stream()
                .filter(m -> m.getChanged() != Meal.MealChanged.REPLACED_OUT)
                .toList();
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getStatus()).isEqualTo(Meal.MealStatus.SKIPPED);
        assertThat(visible.get(0).getFoodName()).isEqualTo("X");

        assertThat(lunchMeals.stream()
                .filter(m -> m.getChanged() == Meal.MealChanged.REPLACED_OUT)
                .allMatch(m -> m.getStatus() == Meal.MealStatus.SKIPPED)).isTrue();
    }

    @Test
    void mealTimeSkippedFlag_shouldBeFalse_ifAnyEatenExists_evenWhenSkippedNonAdditionalExists() {
        Long userId = 2L;
        LocalDate date = LocalDate.of(2026, 2, 5);

        // Given: a target exists (needed to assemble dashboard sections)
        mealTargetRepository.save(MealTarget.builder()
                .userId(userId)
                .targetDate(date)
                .goalType(MemberInfoBody.ExercisePurpose.MAINTAIN)
                .goalCal(2000)
                .goalCarbs(250)
                .goalProtein(120)
                .goalFat(60)
                .build());

        // And: lunch has skipped non-additional items...
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.SKIPPED)
                .isAdditional(false)
                .foodName("SkippedMenu")
                .calories(500).carbs(50).protein(30).fat(20)
                .originalFoodName("SkippedMenu").originalCalories(500).originalCarbs(50).originalProtein(30).originalFat(20)
                .build());

        // ...but also has an additional eaten record (e.g., image-based intake add)
        mealRepository.save(Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(Meal.MealTime.LUNCH)
                .status(Meal.MealStatus.EATEN)
                .isAdditional(true)
                .foodName("AdditionalEaten")
                .calories(200).carbs(10).protein(20).fat(5)
                .originalFoodName("AdditionalEaten").originalCalories(200).originalCarbs(10).originalProtein(20).originalFat(5)
                .build());

        MealDashboardDto dashboard = MealDashboardDto.builder().build();
        mealTargetService.getNutritionAchievement(userId, date, dashboard);

        assertThat(dashboard.getLunch()).isNotNull();
        assertThat(Boolean.TRUE.equals(dashboard.getLunch().getSkipped())).isFalse();
    }
}


