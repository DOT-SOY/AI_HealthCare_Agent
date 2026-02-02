package com.backend.service.ai.chat;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.ai.AIIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 채팅 오케스트레이션 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatOrchestrationServiceImpl implements AIChatOrchestrationService {

    private final AIIntentService aiIntentService;
    private final PainReportChatService painReportChatService;
    private final GeneralChatService generalChatService;
    private final WorkoutChatService workoutChatService;
    private final MealChatService mealChatService;
    private final BodyChatService bodyChatService;
    private final DeliveryChatService deliveryChatService;

    @Override
    public AIChatResponse handleAIChat(AIChatRequest request) {
        log.info("AI 채팅 요청: text={}", request.getText());
        
        // 1. 의도 분류 (Python AI 서버 호출)
        IntentClassificationResult classification = aiIntentService.classifyIntent(request.getText());
        
        String intent = classification.getIntent();
        
        // 2. 의도에 따라 적절한 Service 호출
        AIChatResponse response = switch (intent) {
            case "PAIN_REPORT" -> painReportChatService.handlePainReport(classification);
            case "GENERAL_CHAT" -> generalChatService.handleGeneralChat(classification);
            case "WORKOUT" -> workoutChatService.handleWorkout(classification);
            case "MEAL_QUERY" -> mealChatService.handleMeal(classification);
            case "BODY_QUERY" -> bodyChatService.handleBodyQuery(classification);
            case "DELIVERY_QUERY" -> deliveryChatService.handleDelivery(classification);
            default -> createErrorResponse("알 수 없는 의도입니다.");
        };
        
        return response;
    }

    private AIChatResponse createErrorResponse(String errorMessage) {
        return AIChatResponse.builder()
            .message(errorMessage)
            .intent("ERROR")
            .build();
    }
}

