package com.backend.service.meal;

import com.backend.dto.meal.MealDashboardDto;
import com.backend.dto.meal.MealDto;
import java.time.LocalDate;
import java.util.List;

/**
 * [식단 관리 및 변동 분석 서비스]
 * 1. 실측 식단 저장 및 수동 수정/삭제/추가 처리
 * 2. [핵심] 최초 계획(Original) vs 현재 식단(Current) 비교 분석 (Java Logic)
 * 3. AI 연동(Vision, Advice, Replan) 비동기 처리 제어
 */
public interface MealService {

    /**
     * [대시보드/모달 통합 조회]
     * MealTargetService와 협력하여 그래프 수치 + 식단 리스트 + 분석 문구를 조립합니다.
     */
    MealDashboardDto getMealDashboard(Long userId, LocalDate date);

    /**
     * [식단 수동 등록]
     * AI 추천 외에 사용자가 직접 식단을 추가(Add-on)할 때 사용합니다.
     * isAdditional 필드가 true로 설정됩니다.
     */
    MealDto registerAdditionalMeal(Long userId, MealDto mealDto);

    /**
     * [식단 수정]
     * 기존 식단의 메뉴나 양을 수정합니다. 
     * 이때 original_ 데이터는 보존되어 '변동 분석'의 근거로 남습니다.
     */
    MealDto updateMeal(Long scheduleId, MealDto mealDto);

    /**
     * [식단 삭제/건너뛰기]
     * - 삭제: 사용자가 수동으로 추가한 식단 제거
     * - 건너뛰기: 계획된 식사를 SKIPPED 상태로 변경
     */
    void removeOrSkipMeal(Long scheduleId, boolean isPermanentDelete);

    /**
     * [상태 변경]
     * PLANNED -> EATEN (식사 완료 처리)
     */
    void toggleMealStatus(Long scheduleId, String status);

    /**
     * [비동기 AI 호출: 사진 분석]
     * 이미지 업로드 시 호출되며, 결과는 WebSocket으로 전송됩니다.
     */
    void asyncVisionAnalysis(Long userId, String base64Image);

    /**
     * [비동기 AI 호출: 심층 상담]
     * 하루 식단 전체를 분석하여 영양 조언을 요청합니다.
     */
    void asyncDeepAdvice(Long userId, LocalDate date);

    /**
     * [비동기 AI 호출: 식단 재구성]
     * 초과 섭취나 스킵 발생 시 남은 끼니를 다시 짭니다.
     */
    void asyncMealReplan(Long userId, LocalDate date);

     void generateInitialPlan(Long userId, LocalDate date);
}