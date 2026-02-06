package com.backend.controller.ai;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.request.ChatMessage;
import com.backend.dto.response.AIChatResponse;
import com.backend.service.ai.chat.AIChatOrchestrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * AI Gateway Controller
 *
 * FormData(text, image, conversationHistory)를 받아 AIChatRequest로 조립한 뒤
 * AIChatOrchestrationService에 위임하여 의도 분류 및 MEAL_QUERY, WORKOUT, PAIN_REPORT 등 전체 의도를 처리합니다.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIGatewayController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AIChatOrchestrationService aiChatOrchestrationService;

    /**
     * AI 채팅 처리 (multipart/form-data 수신)
     *
     * 프론트엔드에서 FormData(text, image, conversationHistory)로 전송하므로
     * @RequestParam으로 수신 후 AIChatRequest로 조립하고, 오케스트레이션 서비스에 위임합니다.
     */
    @PostMapping(value = "/chat", consumes = "multipart/form-data")
    public ResponseEntity<AIChatResponse> handleAIChat(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "conversationHistory", required = false) String conversationHistoryJson) {

        AIChatRequest request = buildAIChatRequest(text, image, conversationHistoryJson);
        log.info("AI 채팅 요청: text={}, hasImage={}", request.getText(), request.getImage() != null && !request.getImage().isEmpty());

        AIChatResponse response = aiChatOrchestrationService.handleAIChat(request);
        return ResponseEntity.ok(response);
    }

    private AIChatRequest buildAIChatRequest(String text, MultipartFile image, String conversationHistoryJson) {
        List<ChatMessage> conversationHistory = null;
        if (conversationHistoryJson != null && !conversationHistoryJson.isBlank()) {
            try {
                conversationHistory = OBJECT_MAPPER.readValue(
                        conversationHistoryJson,
                        new TypeReference<List<ChatMessage>>() {});
            } catch (Exception e) {
                log.warn("conversationHistory JSON 파싱 실패, 무시: {}", e.getMessage());
            }
        }
        AIChatRequest request = new AIChatRequest();
        request.setText(text != null ? text : "");
        request.setImage(image);
        request.setConversationHistory(conversationHistory != null ? conversationHistory : Collections.emptyList());
        return request;
    }
}
