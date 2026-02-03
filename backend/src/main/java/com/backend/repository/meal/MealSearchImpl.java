package com.backend.repository.meal;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealCalendarDto;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
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

        // QueryDSL의 Projections.fields는 Integer/Long 타입 불일치 문제가 있으므로,
        // Tuple로 받아서 수동으로 MealCalendarDto로 변환
        List<Tuple> rawResults = queryFactory
                .select(
                        meal.mealDate,
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(meal.calories)
                            .otherwise(0).sum().coalesce(0),
                        new CaseBuilder()
                            .when(meal.isAdditional.isFalse()).then(meal.originalCalories)
                            .otherwise(0).sum().coalesce(0),
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.EATEN)).then(1)
                            .otherwise(0).sum().coalesce(0),
                        new CaseBuilder()
                            .when(meal.status.eq(Meal.MealStatus.SKIPPED)).then(1)
                            .otherwise(0).sum().coalesce(0)
                )
                .from(meal)
                .where(
                        meal.userId.eq(userId),
                        meal.mealDate.between(startDate, endDate)
                )
                .groupBy(meal.mealDate)
                .orderBy(meal.mealDate.asc())
                .fetch();
        
        // Tuple을 MealCalendarDto로 변환 (Integer -> Long 변환 포함)
        List<MealCalendarDto> result = new ArrayList<>();
        for (Tuple row : rawResults) {
            MealCalendarDto dto = new MealCalendarDto();
            dto.setMealDate(row.get(meal.mealDate));
            dto.setTotalEatenCalories(row.get(1, Integer.class));
            dto.setTotalOriginalCalories(row.get(2, Integer.class));
            // Integer를 Long으로 변환
            Integer eatenCount = row.get(3, Integer.class);
            Integer skippedCount = row.get(4, Integer.class);
            dto.setEatenCount(eatenCount != null ? eatenCount.longValue() : 0L);
            dto.setSkippedCount(skippedCount != null ? skippedCount.longValue() : 0L);
            result.add(dto);
        }

        return result;
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

