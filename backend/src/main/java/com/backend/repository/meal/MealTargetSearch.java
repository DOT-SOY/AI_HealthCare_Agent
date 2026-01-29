package com.backend.repository.meal;

import com.backend.domain.meal.MealTarget;
import java.time.LocalDate;
import java.util.List;

/**
 * [목표 데이터 복합 조회 인터페이스]
 * QueryDSL을 사용하여 목표 데이터의 이력 조회 및 기간 검색을 정의합니다.
 */
public interface MealTargetSearch {

    /**
     * 특정 날짜 이전의 가장 최신 목표 조회
     * 용도: 만약 오늘 날짜(10일)에 목표 설정이 안 되어 있다면,
     * 가장 최근(예: 9일)에 설정했던 목표를 자동으로 불러와 적용하기 위함입니다.
     * (매일 목표를 입력하지 않아도 이전 설정을 유지하게 해주는 핵심 로직용)
     */
    MealTarget findLatestTargetBeforeDate(Long userId, LocalDate date);

    /**
     * 특정 기간 동안의 목표 설정 이력 조회
     * 용도: 다이어트 기간 동안 목표 칼로리를 어떻게 줄여나갔는지 그래프로 보여줄 때 사용합니다.
     */
    List<MealTarget> findTargetsBetweenDates(Long userId, LocalDate startDate, LocalDate endDate);
}

