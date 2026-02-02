package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * GENERAL_CHAT 의도 처리 서비스
 */
public interface GeneralChatService {
    /**
     * GENERAL_CHAT 의도 처리
     * 
     * 처리 방식: 의도 분류와 답변을 한 번에 받아서 그대로 반환
     * - Python AI 서버의 /chat 엔드포인트에서 의도 분류와 함께 답변도 생성
     * - classification.getAiAnswer()에 이미 생성된 답변이 포함됨
     * - DB 저장 없이 AI 응답만 반환
     */
    AIChatResponse handleGeneralChat(IntentClassificationResult classification);
}

