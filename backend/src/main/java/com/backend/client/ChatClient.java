package com.backend.client;

import com.backend.dto.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * Python AI 서버의 /chat 엔드포인트를 호출하여 의도를 분류합니다.
     * 
     * @param userInput 사용자 입력
     * @return 의도 분류 결과
     */
    public ChatResponse classifyIntent(String userInput) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", userInput);
        
        return baseAIClient.postRequest("/chat", requestBody, ChatResponse.class);
    }
}
