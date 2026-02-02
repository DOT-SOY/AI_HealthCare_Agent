package com.backend.controller.ai;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.request.ChatMessage;
import com.backend.dto.response.AIChatResponse;
import com.backend.service.ai.chat.AIChatOrchestrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

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
    private final ObjectMapper objectMapper;
    
    /**
     * AI 채팅 처리 (텍스트 및 이미지 지원)
     * 
     * multipart/form-data로 텍스트, 이미지, 대화 히스토리를 받습니다.
     * 
     * @param text 사용자 입력 텍스트 (선택적)
     * @param image 첨부된 이미지 파일 (선택적)
     * @return AI 응답
     */
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AIChatResponse> handleAIChat(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "conversationHistory", required = false) String conversationHistoryJson) {
        
        try {
            AIChatRequest request = new AIChatRequest();
            request.setText(text);
            request.setImage(image);
            
            // 대화 히스토리 파싱
            if (conversationHistoryJson != null && !conversationHistoryJson.trim().isEmpty()) {
                try {
                    List<ChatMessage> history = objectMapper.readValue(
                        conversationHistoryJson,
                        new TypeReference<List<ChatMessage>>() {}
                    );
                    request.setConversationHistory(history);
                } catch (Exception e) {
                    log.warn("대화 히스토리 파싱 실패: {}", e.getMessage());
                    request.setConversationHistory(new ArrayList<>());
                }
            }
            
            AIChatResponse response = aiChatOrchestrationService.handleAIChat(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("AI 채팅 처리 실패", e);
            AIChatResponse errorResponse = AIChatResponse.builder()
                .intent("ERROR")
                .message("처리 중 오류가 발생했습니다: " + e.getMessage())
                .build();
            return ResponseEntity.ok(errorResponse);
        }
    }
}
