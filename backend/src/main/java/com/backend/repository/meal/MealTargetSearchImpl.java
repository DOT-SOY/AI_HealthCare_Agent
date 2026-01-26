package com.backend.repository.meal;

import com.backend.domain.meal.MealTarget;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.backend.domain.meal.QMealTarget.mealTarget;

@Repository
@RequiredArgsConstructor
public class MealTargetSearchImpl implements MealTargetSearch {

    private final JPAQueryFactory queryFactory;

    @Override
    public MealTarget findLatestTargetBeforeDate(Long userId, LocalDate date) {
        // [핵심 로직]
        // 해당 날짜(date)를 포함하여 그 이전 과거 데이터 중에서
        // 가장 날짜가 최신인(orderBy desc) 딱 1개(limit 1)를 가져온다.
        // -> 오늘 목표가 없으면 어제 목표를, 어제도 없으면 3일 전 목표를 가져와서 적용함.
        return queryFactory
                .selectFrom(mealTarget)
                .where(
                        mealTarget.userId.eq(userId),
                        mealTarget.targetDate.loe(date) // targetDate <= date
                )
                .orderBy(mealTarget.targetDate.desc()) // 최신순 정렬
                .limit(1) // 딱 하나만
                .fetchOne();
    }

    @Override
    public List<MealTarget> findTargetsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate) {
        // [기간 조회]
        // 캘린더나 그래프를 그리기 위해 특정 기간의 목표 변화를 모두 가져옴
        return queryFactory
                .selectFrom(mealTarget)
                .where(
                        mealTarget.userId.eq(userId),
                        mealTarget.targetDate.between(startDate, endDate)
                )
                .orderBy(mealTarget.targetDate.asc()) // 날짜 순서대로
                .fetch();
    }
}