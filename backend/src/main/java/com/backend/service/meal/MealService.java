package com.backend.service.meal;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealDashboardDto;
import com.backend.dto.meal.MealDto;
import com.backend.dto.meal.AiMealVisionFollowupDto;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 날짜와 식사 시간으로 식단 조회
     * @param userId 회원 ID
     * @param date 날짜 (null이면 오늘)
     * @param mealTime 식사 시간 (BREAKFAST/LUNCH/DINNER, null이면 하루 전체)
     * @return 식단 목록
     */
    List<MealDto> getMealsByDateAndTime(Long userId, LocalDate date, Meal.MealTime mealTime);

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
     * 
     * 변경: CompletableFuture 반환으로 진정한 비동기 처리
     */
    CompletableFuture<Void> asyncVisionAnalysis(Long userId, String base64Image);

    /**
     * [비동기 AI 호출: 심층 상담]
     * 하루 식단 전체를 분석하여 영양 조언을 요청합니다.
     * 
     * 변경: CompletableFuture 반환으로 진정한 비동기 처리
     */
    CompletableFuture<Void> asyncDeepAdvice(Long userId, LocalDate date);

    /**
     * [비동기 AI 호출: 식단 재구성]
     * 초과 섭취나 스킵 발생 시 남은 끼니를 다시 짭니다.
     * 
     * 변경: CompletableFuture 반환으로 진정한 비동기 처리
     */
    CompletableFuture<Void> asyncMealReplan(Long userId, LocalDate date);

    /**
     * [비동기 끼니 생략 재배분]
     * - 끼니 전체 생략 시, 생략된 끼니의 영양(탄/단/지/칼로리)을
     *   아직 완료되지 않은 끼니에 "균등 분배"하여 추가 메뉴로 반영합니다.
     * - 템플릿 복제가 아니라, meal_foods(개별 음식 풀)에서 음식들을 골라 추가합니다.
     */
    CompletableFuture<Void> asyncRedistributeAfterMealTimeSkip(Long userId, LocalDate date, String skippedMealTime);

    /**
     * [식단 계획 업데이트]
     * PLANNED 상태의 기존 식단을 삭제하고 새로운 계획으로 교체합니다.
     * 
     * 변경 이유:
     * - private 메서드를 Service 메서드로 분리하여 트랜잭션 경계 명확화
     * - @Transactional 적용으로 DB 저장 보장
     * - 재사용성 향상
     */
    void updatePlannedMeals(Long userId, LocalDate date, List<MealDto> newPlans);

    /**
     * [AI 식단 생성] 전역 모달(자연어)에서 "7일치/한달치 식단" 요청 시 사용
     * - today부터 미래로 생성
     * - 생성된 PLANNED 식단을 DB에 저장(기존 PLANNED는 교체)
     */
    CompletableFuture<Integer> asyncGeneratePlanFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType);

    /**
     * [AI 식단 생성 - 빈 날짜만 채우기]
     * - 요청 기간 중 이미 PLANNED가 있는 날짜는 유지
     * - 비어있는 날짜만 새로 생성하여 저장
     */
    CompletableFuture<Integer> asyncGeneratePlanFillMissingFromAiChat(Long userId, LocalDate startDate, Integer periodDays, String goalType);

    /**
     * [Vision Followup] 이미지 분석 후 사용자의 자연어 지시를 해석(추가/대체/취소/질문)합니다.
     * - 실제 DB 반영은 프론트가 operation에 맞춰 수행합니다.
     */
    CompletableFuture<AiMealVisionFollowupDto.Response> visionFollowup(Long userId, AiMealVisionFollowupDto.Request request);

    /**
     * [Vision Apply - ADD]
     * 이미지 분석 결과(음식명/영양정보)를 사용자가 "추가"로 반영하길 원할 때,
     * 추가 섭취(EATEN, isAdditional=true)로 기록합니다.
     *
     * - followup 의도 판단은 MealCommand(LLM)에서 수행
     * - DB 반영은 백엔드에서 수행 (프론트 주도 로직 제거)
     */
    String applyVisionAdd(Long userId, LocalDate date, String mealTimeOrNull, java.util.Map<String, Object> analyzedFood);

    /**
     * [Vision Apply - REPLACE]
     * 이미지 분석 결과를 특정 끼니(아침/점심/저녁)의 계획 식단 대신으로 기록합니다.
     * - 해당 끼니의 기존 PLANNED(추가식단 제외)를 찾아 1개는 EATEN으로 교체, 나머지는 SKIPPED로 처리합니다.
     * - 대체할 계획이 없으면 ADD로 fallback 합니다.
     */
    String applyVisionReplace(Long userId, LocalDate date, String mealTimeOrNull, java.util.Map<String, Object> analyzedFood);

    /**
     * [끼니 단위 완료/완료취소 토글]
     * - 해당 date+mealTime의 계획(PLANNED)이 있으면 EATEN으로 변경
     * - 계획이 없고(이미 완료 상태) EATEN(추가식단 제외)이 있으면 PLANNED로 복구
     */
    String toggleMealTimeComplete(Long userId, LocalDate date, String mealTime);

    /**
     * [끼니 단위 생략/생략취소 토글]
     * - 해당 date+mealTime의 계획(PLANNED)이 있으면 SKIPPED로 변경
     * - 계획이 없고 SKIPPED(추가식단 제외)이 있으면 PLANNED로 복구
     */
    String toggleMealTimeSkip(Long userId, LocalDate date, String mealTime);

    /**
     * [항목(음식명) 단위 완료/생략 토글]
     * - date(필수) + (mealTime 선택) + foodName(부분 포함 매칭)으로 대상 항목을 찾아 상태 토글
     * - 모호하면 안내 메시지를 반환하고 변경하지 않습니다.
     */
    String toggleItemByFoodName(Long userId, LocalDate date, String mealTimeOrNull, String foodName, String mode);
}