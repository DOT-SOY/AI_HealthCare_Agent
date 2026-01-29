package com.backend.repository.meal;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealCalendarDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.backend.domain.meal.QMeal.meal;

@Repository
@RequiredArgsConstructor
public class MealSearchImpl implements MealSearch {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MealCalendarDto> findMonthlyMealSummary(Long userId, LocalDate yearMonth) {
        LocalDate startDate = yearMonth.withDayOfMonth(1);
        LocalDate endDate = yearMonth.withDayOfMonth(yearMonth.lengthOfMonth());

        return queryFactory
                .select(Projections.fields(MealCalendarDto.class,
                        meal.mealDate,
                        
                        // 1. 실제 섭취한 칼로리 합계 (EATEN 상태만)
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(meal.calories)
                            .otherwise(0).sum().coalesce(0).as("totalEatenCalories"),

                        // 2. 원래 계획했던 칼로리 합계 (분석 비교용 - 상태 무관하게 Original 값 합산)
                        //    단, 추가된 식단(isAdditional)은 계획에 없던 거니까 제외해야 정확한 비교 가능
                        new CaseBuilder()
                            .when(meal.isAdditional.isFalse()).then(meal.originalCalories)
                            .otherwise(0).sum().coalesce(0).as("totalOriginalCalories"),

                        // 3. 섭취 완료 횟수
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(1)
                            .otherwise(0).sum().coalesce(0).as("eatenCount"),

                        // 4. 거른 끼니(SKIPPED) 횟수 (분석 데이터용)
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.SKIPPED)).then(1)
                            .otherwise(0).sum().coalesce(0).as("skippedCount")
                ))
                .from(meal)
                .where(
                        meal.userId.eq(userId),
                        meal.mealDate.between(startDate, endDate)
                )
                .groupBy(meal.mealDate)
                .orderBy(meal.mealDate.asc())
                .fetch();
    }

    @Override
    public List<Meal> findMealsByDateAndUser(Long userId, LocalDate date) {
        return queryFactory
                .selectFrom(meal)
                .where(
                        meal.userId.eq(userId),
                        meal.mealDate.eq(date)
                )
                .orderBy(
                        meal.mealTime.asc(),      // 아침->점심->저녁->간식
                        meal.isAdditional.asc(),  // 정규 식사 -> 추가 식사
                        meal.scheduleId.asc()     // 등록순
                )
                .fetch();
    }

    @Override
    public List<Meal> findMealsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        return queryFactory
                .selectFrom(meal)
                .where(
                        meal.userId.eq(userId),
                        meal.mealDate.between(startDate, endDate)
                )
                .orderBy(meal.mealDate.asc(), meal.mealTime.asc())
                .fetch();
    }
}

