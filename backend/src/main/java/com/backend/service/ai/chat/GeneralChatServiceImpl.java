package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * GENERAL_CHAT 의도 처리 서비스 구현
 */
@Service
@Slf4j
public class GeneralChatServiceImpl implements GeneralChatService {

    @Override
    public AIChatResponse handleGeneralChat(IntentClassificationResult classification) {
        String aiAnswer = classification.getAiAnswer();
        
        // aiAnswer가 null이거나 빈 문자열인 경우 처리
        if (aiAnswer == null || aiAnswer.trim().isEmpty()) {
            log.warn("GENERAL_CHAT: Python AI 서버에서 aiAnswer가 비어있습니다. intent={}", classification.getIntent());
            aiAnswer = "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.";
        }
        
        log.info("GENERAL_CHAT 응답: intent={}, answerLength={}", classification.getIntent(), aiAnswer.length());
        
        return AIChatResponse.builder()
            .message(aiAnswer)
            .intent("GENERAL_CHAT")
            .build();
    }
}

