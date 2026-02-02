package com.backend.service.ai.chat;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.ai.AIIntentService;
import com.backend.service.member.CurrentMemberService;
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
    private final CommerceChatService commerceChatService;
    private final CurrentMemberService currentMemberService;

    @Override
    public AIChatResponse handleAIChat(AIChatRequest request) {
        log.info("AI 채팅 요청: text={}", request.getText());
        
        // 0. 상품 추천 플로우 중인지 확인 (세션 상태 확인)
        // "응", "예", "네" 같은 짧은 답변이 상품 추천 플로우 중이면 자동으로 라우팅
        try {
            Long memberId = currentMemberService.getCurrentMemberIdOptional().orElse(null);
            if (memberId != null) {
                String sessionId = "commerce_" + memberId;
                // CommerceChatService에서 세션 상태를 확인하여 상품 추천 플로우 중이면 처리
                if (commerceChatService instanceof CommerceChatServiceImpl) {
                    boolean isInCommerceFlow = ((CommerceChatServiceImpl) commerceChatService).isInCommerceFlow(sessionId);
                    if (isInCommerceFlow) {
                        log.info("상품 추천 플로우 중 감지: sessionId={}, text={}", sessionId, request.getText());
                        // 의도 분류 없이 바로 CommerceChatService로 라우팅
                        IntentClassificationResult dummyClassification = IntentClassificationResult.builder()
                                .intent("PRODUCT_RECOMMEND")
                                .action("RECOMMEND")
                                .build();
                        return ((CommerceChatServiceImpl) commerceChatService).handleCommerceRecommend(dummyClassification, request.getText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("상품 추천 플로우 확인 중 오류 (무시하고 계속 진행): {}", e.getMessage());
        }
        
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
            case "PRODUCT_RECOMMEND" -> {
                // 사용자 발화 텍스트를 commerce 서비스에 전달
                if (commerceChatService instanceof CommerceChatServiceImpl) {
                    yield ((CommerceChatServiceImpl) commerceChatService).handleCommerceRecommend(classification, request.getText());
                } else {
                    yield commerceChatService.handleCommerceRecommend(classification);
                }
            }
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

