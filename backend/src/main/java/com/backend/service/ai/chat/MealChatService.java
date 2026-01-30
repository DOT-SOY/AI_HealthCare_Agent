package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * MEAL_QUERY 의도 처리 서비스
 */
public interface MealChatService {
    /**
     * MEAL_QUERY 의도 처리 (대분류: intent)
     *
     * action(소분류)에 따라 분기:
     * - QUERY: 식단 조회
     * - RECOMMEND: 식단 추천 (추후 구현)
     * - MODIFY: 식단 수정 (추후 구현)
     */
    AIChatResponse handleMeal(IntentClassificationResult classification);
}

