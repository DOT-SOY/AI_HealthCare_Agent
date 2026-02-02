package com.backend.service.ai.chat;

import com.backend.client.FoodAnalysisClient;
import com.backend.client.ImageClassificationClient;
import com.backend.client.InbodyAnalysisClient;
import com.backend.dto.request.AIChatRequest;
import com.backend.dto.request.ChatMessage;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.ImageClassificationResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.ai.AIIntentService;
import com.backend.service.member.CurrentMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * AI 채팅 오케스트레이션 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatOrchestrationServiceImpl implements AIChatOrchestrationService {

    private static final Set<String> TRIGGER_KEYWORDS = Set.of(
            // 동의 / 승인
            "응", "응응", "그래", "그래요", "좋아", "좋아요", "알겠어", "알겠습니다",
            "오케이", "오케", "OK", "ㅇㅋ",

            // 이전 대화 지시
            "그거", "그거 해줘", "그걸로", "그렇게", "그렇게 해줘",
            "그 내용", "그 방식", "그 방법", "그 기준",
            "아까", "아까 그거", "방금 그거", "그럼",
            "이전 거", "전에 말한 거", "조금 전에 말한 거",

            // 이어서 / 계속
            "이어서", "계속", "계속해줘", "그 다음", "다음 단계", "그 다음으로",

            // 반복
            "다시", "다시 해줘", "한 번 더", "다시 한번",

            // 목적어 생략
            "해줘", "해", "해봐", "보여줘", "알려줘", "정리해줘"
    );

    private final AIIntentService aiIntentService;
    private final PainReportChatService painReportChatService;
    private final GeneralChatService generalChatService;
    private final WorkoutChatService workoutChatService;
    private final MealChatService mealChatService;
    private final BodyChatService bodyChatService;
    private final DeliveryChatService deliveryChatService;
    private final CommerceChatService commerceChatService;
    private final CurrentMemberService currentMemberService;
    private final ImageClassificationClient imageClassificationClient;
    private final InbodyAnalysisClient inbodyAnalysisClient;
    private final FoodAnalysisClient foodAnalysisClient;

    @Override
    public AIChatResponse handleAIChat(AIChatRequest request) {
        // 1. 이미지가 있으면 이미지 분류 후 라우팅
        if (request.getImage() != null && !request.getImage().isEmpty()) {
            return handleImageRequest(request);
        }

        // 2. 텍스트 처리
        String text = request.getText();
        if (text == null || text.trim().isEmpty()) {
            return createErrorResponse("텍스트 또는 이미지를 입력해주세요.");
        }
        
        log.info("AI 채팅 요청: text={}", text);

        // 3. 트리거 키워드 감지
        boolean isTrigger = isTriggerMessage(text);
        IntentClassificationResult classification;

        if (isTrigger && request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
            // 컨텍스트 포함 의도 분석
            String context = buildContext(request.getConversationHistory());
            classification = aiIntentService.classifyIntentWithContext(context, text);
        } else {
            // 일반 의도 분석
            classification = aiIntentService.classifyIntent(text);
        }
        
        String intent = classification.getIntent();
        
        // 4. 의도에 따라 적절한 Service 호출
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

    /**
     * 이미지 요청 처리
     */
    private AIChatResponse handleImageRequest(AIChatRequest request) {
        MultipartFile image = request.getImage();
        log.info("이미지 분석 요청: filename={}, size={} bytes",
            image.getOriginalFilename(), image.getSize());

        try {
            // 이미지 분류
            ImageClassificationResponse classification = imageClassificationClient.classifyImage(image);
            String imageType = classification.getType();
            Double confidence = classification.getConfidence();

            log.info("이미지 분류 결과: type={}, confidence={}, filename={}",
                imageType, confidence, image.getOriginalFilename());

            // 분류 결과에 따라 라우팅
            if ("inbody".equals(imageType)) {
                log.info("인바디 분석으로 라우팅: filename={}", image.getOriginalFilename());
                return inbodyAnalysisClient.analyzeInbody(image);
            } else {
                // food 또는 unknown 모두 음식 분석으로 라우팅
                log.info("음식 분석으로 라우팅: filename={}", image.getOriginalFilename());
                return foodAnalysisClient.analyzeFood(image);
            }

        } catch (Exception e) {
            log.error("이미지 분석 실패", e);
            return createErrorResponse("이미지 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 트리거 키워드 감지
     */
    private boolean isTriggerMessage(String text) {
        if (text == null) {
            return false;
        }

        String trimmedText = text.trim();
        String lowerText = trimmedText.toLowerCase();

        return TRIGGER_KEYWORDS.stream()
            .anyMatch(keyword -> {
                String lowerKeyword = keyword.toLowerCase();
                return lowerText.equals(lowerKeyword) ||
                       lowerText.startsWith(lowerKeyword + " ");
            });
    }

    /**
     * 대화 히스토리에서 컨텍스트 구성
     */
    private String buildContext(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (ChatMessage msg : history) {
            String role = "assistant".equals(msg.getRole()) ? "AI" : "User";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        return context.toString();
    }

    private AIChatResponse createErrorResponse(String errorMessage) {
        return AIChatResponse.builder()
            .message(errorMessage)
            .intent("ERROR")
            .build();
    }
}

