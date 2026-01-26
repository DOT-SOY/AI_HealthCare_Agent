package com.backend.service.meal;

import com.backend.dto.meal.MealTargetDto;
import com.backend.dto.meal.MealDashboardDto;
import java.time.LocalDate;

/**
 * [목표 영양 수치 관리 서비스 인터페이스]
 * 일일 영양 목표 설정 및 목표 대비 달성도 판정 기능을 정의합니다.
 */
public interface MealTargetService {

    /**
     * 특정 날짜의 유효 목표 조회 (자동 승계 로직 포함)
     */
    MealTargetDto getTargetByDate(Long userId, LocalDate date);

    /**
     * 목표 수치 저장 및 업데이트
     */
    MealTargetDto updateTarget(Long userId, MealTargetDto targetDto);

    /**
     * 대시보드용 영양소 달성 현황(그래프 및 상태) 데이터 조립
     * (실제 ±10% 판정 로직은 구현체에서 실행됨)
     */
    void getNutritionAchievement(Long userId, LocalDate date, MealDashboardDto dashboardDto);

    /**
     * AI 심층 피드백(영양 조언) 저장
     */
    void updateAiFeedback(Long userId, LocalDate date, String feedback);
}