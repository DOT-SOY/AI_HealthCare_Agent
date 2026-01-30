package com.backend.controller.ai;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.service.ai.chat.AIChatOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Gateway Controller
 * 
 * AI 채팅 요청을 받아 AIChatOrchestrationService에 위임합니다.
 * 비즈니스 로직은 Service 계층에서 처리됩니다.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIGatewayController {
    
    private final AIChatOrchestrationService aiChatOrchestrationService;
    
    /**
     * 텍스트 기반 AI 채팅 처리
     * 
     * AIChatOrchestrationService에 위임하여 처리합니다.
     */
    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> handleAIChat(@RequestBody AIChatRequest request) {
        AIChatResponse response = aiChatOrchestrationService.handleAIChat(request);
        return ResponseEntity.ok(response);
    }
}
