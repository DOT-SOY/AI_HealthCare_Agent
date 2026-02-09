package com.backend.service.meal.context;

import com.backend.dto.meal.MealAiContextDto;

/**
 * [Meal AI 컨텍스트 서비스]
 * - 식단 도메인 대화형 처리를 위한 컨텍스트를 Redis에 저장/조회합니다.
 * - 정책: 3턴(6 메시지) + TTL 30분
 */
public interface MealAiContextService {

    MealAiContextDto get(Long userId);

    MealAiContextDto appendUser(Long userId, String text);

    MealAiContextDto appendAssistant(Long userId, String text);

    MealAiContextDto setPending(Long userId, String type, java.util.Map<String, Object> data);

    MealAiContextDto clearPending(Long userId);

    /**
     * [컨텍스트 리셋]
     * - 사용자 식단 대화 히스토리 + pending 상태를 초기화합니다.
     * - 전역 채팅의 "초기화/리셋" 버튼과 연동됩니다.
     */
    void reset(Long userId);
}



