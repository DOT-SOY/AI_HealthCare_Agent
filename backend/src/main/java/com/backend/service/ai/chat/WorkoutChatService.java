package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * WORKOUT 의도 처리 서비스
 */
public interface WorkoutChatService {
    /**
     * WORKOUT 의도 처리 (대분류: intent)
     *
     * action(소분류)에 따라 분기:
     * - QUERY: 루틴 조회 (운동 기록, 회고 등 포함)
     * - RECOMMEND: 운동 추천 (추후 구현)
     * - MODIFY: 루틴 수정 (추후 구현)
     */
    AIChatResponse handleWorkout(IntentClassificationResult classification);
}

