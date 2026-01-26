package com.backend.repository.meal;

import com.backend.domain.meal.Meal;
import com.backend.domain.meal.QMeal;
import com.backend.dto.meal.MealCalendarDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.backend.domain.meal.QMeal.meal;

/**
 * [식단 데이터 검색 구현체]
 * QueryDSL을 사용하여 복잡한 통계 및 조회 쿼리를 수행합니다.
 * Null Safety를 보장하며, 대용량 데이터 조회 시 성능 최적화를 고려했습니다.
 */
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
                        // [Null Safety] 칼로리가 NULL일 경우 0으로 처리하여 집계 오류 방지
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(meal.calories)
                            .otherwise(0).sum().coalesce(0).as("totalCalories"),
                        
                        // [Count] 섭취 횟수 집계
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(1)
                            .otherwise(0).sum().coalesce(0).as("eatenCount")
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
                // [Sort] 아침(1) -> 점심(2) -> 저녁(3) -> 간식(4) 순서 보장
                .orderBy(
                        meal.mealTime.asc(),
                        meal.isAdditional.asc(), // 추가 식단은 하단 배치
                        meal.scheduleId.asc()    // 동시간대 등록 시 먼저 등록한 순
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