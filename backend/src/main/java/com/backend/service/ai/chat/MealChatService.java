package com.backend.service.ai.chat;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * MEAL_QUERY 의도 처리 서비스
 */
public interface MealChatService {
    /**
     * MEAL_QUERY 의도 처리 (대분류: intent)
     *
     * 로컬 폴더 기준: MealCommandClient(/api/meal/command)로 자연어를 구조화된 operation으로 변환한 뒤,
     * 백엔드는 operation 실행만 담당합니다.
     */
    AIChatResponse handleMeal(IntentClassificationResult classification, AIChatRequest request);
}
