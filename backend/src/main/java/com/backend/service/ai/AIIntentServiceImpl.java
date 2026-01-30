package com.backend.service.ai;

import com.backend.client.ChatClient;
import com.backend.dto.response.ChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIIntentServiceImpl implements AIIntentService {

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
                .action(chatResponse.getAction())
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

