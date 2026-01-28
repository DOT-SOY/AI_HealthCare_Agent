package com.backend.repository.meal;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealCalendarDto;

import java.time.LocalDate;
import java.util.List;

/**
 * [식단 복합 조회 인터페이스]
 * QueryDSL을 사용하여 구현될 복잡한 검색 메서드를 정의합니다.
 * 단순 CRUD는 JpaRepository가, 통계 및 집계 쿼리는 이 인터페이스가 담당합니다.
 */
public interface MealSearch {

    /**
     * 월간 캘린더 데이터 조회
     * 특정 월의 모든 식단 데이터를 요약하여 날짜별 상태(성공/실패/진행중)를 반환합니다.
     * @param userId 회원 ID
     * @param yearMonth 조회할 연월 (예: 2024-05-01 기준)
     * @return 날짜별 요약 데이터 리스트
     */
    List<MealCalendarDto> findMonthlyMealSummary(Long userId, LocalDate yearMonth);

    /**
     * 특정 날짜의 모든 식단 조회 (정렬 포함)
     * 아침 -> 점심 -> 저녁 -> 간식 순서 및 추가 식단 순서대로 정렬하여 반환합니다.
     * 상세 페이지(모달)에서 사용됩니다.
     */
    List<Meal> findMealsByDateAndUser(Long userId, LocalDate date);
    
    /**
     * 특정 기간 동안의 식단 섭취 이력 조회 (통계용)
     * 주간/월간 리포트 생성 시 사용될 수 있습니다.
     */
    List<Meal> findMealsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate);
}