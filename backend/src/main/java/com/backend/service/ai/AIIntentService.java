package com.backend.service.ai;

import com.backend.client.ChatClient;
import com.backend.dto.response.ChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

public interface AIIntentService {
    /**
     * 사용자 입력을 분석하여 의도를 분류합니다.
     * Python AI 서버의 /chat 엔드포인트를 호출하여 LLM 기반 의도 분류를 수행합니다.
     * 
     * @param userInput 사용자 입력 텍스트
     * @return 의도 분류 결과 (intent, entities, aiAnswer, requiresDbCheck)
     */
    IntentClassificationResult classifyIntent(String userInput);

    @Service
    @RequiredArgsConstructor
    @Slf4j
    class AIIntentServiceImpl implements AIIntentService {

        private final ChatClient chatClient;

        @Override
        public IntentClassificationResult classifyIntent(String userInput) {
            log.info("의도 분류 요청: userInput={}", userInput);

            try {
                ChatResponse chatResponse = chatClient.classifyIntent(userInput);

                log.info("의도 분류 결과: intent={}, aiAnswer={}, aiAnswerLength={}",
                    chatResponse.getIntent(),
                    chatResponse.getAiAnswer() != null ? chatResponse.getAiAnswer().substring(0, Math.min(50, chatResponse.getAiAnswer().length())) : "null",
                    chatResponse.getAiAnswer() != null ? chatResponse.getAiAnswer().length() : 0);

                return IntentClassificationResult.builder()
                    .intent(chatResponse.getIntent())
                    .entities(chatResponse.getEntities())
                    .aiAnswer(chatResponse.getAiAnswer())
                    .requiresDbCheck(chatResponse.isRequiresDbCheck())
                    .build();
            } catch (Exception e) {
                log.error("의도 분류 실패: userInput={}, error={}", userInput, e.getMessage(), e);
                throw new RuntimeException("AI 서버 통신 실패: " + e.getMessage(), e);
            }
        }
    }
}
