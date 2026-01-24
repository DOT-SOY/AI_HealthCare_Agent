package com.backend.serviceImpl.ai;

import com.backend.client.ChatClient;
import com.backend.dto.response.ChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.ai.AIIntentService;
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
        log.debug("의도 분류 요청: userInput={}", userInput);
        
        ChatResponse chatResponse = chatClient.classifyIntent(userInput);
        
        return IntentClassificationResult.builder()
            .intent(chatResponse.getIntent())
            .entities(chatResponse.getEntities())
            .aiAnswer(chatResponse.getAiAnswer())
            .requiresDbCheck(chatResponse.isRequiresDbCheck())
            .build();
    }
}
