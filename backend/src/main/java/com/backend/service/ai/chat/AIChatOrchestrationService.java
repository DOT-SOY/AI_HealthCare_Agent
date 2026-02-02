package com.backend.service.ai.chat;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;

/**
 * AI 채팅 오케스트레이션 서비스
 * 
 * 의도 분류 및 의도별 Service 라우팅을 담당합니다.
 */
public interface AIChatOrchestrationService {
    /**
     * 텍스트 기반 AI 채팅 처리
     * 
     * 처리 흐름:
     * 1. 의도 분류 (Python AI 서버 /chat 호출)
     * 2. 의도에 따라 분기:
     *    - GENERAL_CHAT: Python AI 서버에서 이미 생성한 답변 그대로 반환
     *    - 기타 기능: 백엔드 Service를 통해 Python AI 서버의 특정 함수 재호출
     */
    AIChatResponse handleAIChat(AIChatRequest request);
}

