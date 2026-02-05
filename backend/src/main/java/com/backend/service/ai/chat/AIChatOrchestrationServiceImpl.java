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
import com.backend.service.ai.ConversationContextService;
import com.backend.service.meal.MealAiContextService;
import com.backend.dto.meal.MealAiContextDto;
import com.backend.service.member.CurrentMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 채팅 오케스트레이션 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatOrchestrationServiceImpl implements AIChatOrchestrationService {

    // Speech Act 트리거 키워드 (단순 반응형 발화)
    private static final Set<String> SPEECH_ACT_KEYWORDS = Set.of(
            "응", "네", "그래", "그래요", "알겠어", "알겠어요",
            "좋아", "좋아요", "오케이", "OK", "ㅇㅇ"
    );

    // 기존 트리거 키워드 (이전 대화 지시, 이어서, 반복 등)
    private static final Set<String> TRIGGER_KEYWORDS = Set.of(
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
    private final ConversationContextService conversationContextService;
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
    private final MealAiContextService mealAiContextService;
    private final CurrentMemberService currentMemberService;
    private final com.backend.service.meal.MealService mealService;

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
        
        // 3. Speech Act 감지 (단순 반응형 발화)
        boolean isSpeechAct = isSpeechActMessage(text);

        // 4. Speech Act가 아닌 경우에만 트리거 키워드 감지 (기존 로직)
        boolean isTrigger = !isSpeechAct && isTriggerMessage(text);


        // commerce 세션 가드: Redis(ai-server)에 세션이 있으면 우선 commerce로 위임하되,
        //      응답의 error 코드(SESSION_EXPIRED/OFF_TOPIC/FLOW_COMPLETED)에 따라 재분류 또는 플로우 종료를 판단한다.
        boolean triedCommerce = false;
        try {
            Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
            String sessionId = "commerce_" + memberId;
            boolean inFlow = commerceChatService.isInCommerceFlow(sessionId);
            if (inFlow) {
                log.info("commerce 세션 가드: in_flow=true, sessionId={}, 우선 commerce로 위임", sessionId);
                triedCommerce = true;
                AIChatResponse commerceResponse = commerceChatService.handleCommerceRecommendBySession(sessionId, text);

                // ai-server 응답의 error 코드 확인
                String commerceError = null;
                Object data = commerceResponse.getData();
                if (data instanceof Map<?, ?> dataMap) {
                    Object errorValue = ((Map<?, ?>) dataMap).get("error");
                    if (errorValue instanceof String) {
                        commerceError = (String) errorValue;
                    }
                }

                if ("SESSION_EXPIRED".equals(commerceError) || "OFF_TOPIC".equals(commerceError)) {
                    // 세션 만료 또는 딴소리로 인한 플로우 종료: 같은 발화로 intent 재분류 진행
                    log.info("commerce 플로우 종료 신호 감지: error={}, sessionId={}", commerceError, sessionId);
                    // fall-through: 아래 intent 분류 로직으로 이어진다.
                } else {
                    // FLOW_COMPLETED 또는 기타/없음: commerce 응답을 그대로 반환
                    return commerceResponse;
                }
            }
        } catch (Exception e) {
            // 비로그인 등으로 memberId를 얻지 못하면 가드 건너뛰고 기존 의도 분류 진행
        }

        // 3. 트리거 키워드 감지
        IntentClassificationResult classification;
        
        if (isSpeechAct) {
            // Speech Act 감지 시: 저장된 컨텍스트 조회 (JWT에서 email 추출)
            String email = getEmailIfNeeded();
            if (email != null) {
                IntentClassificationResult savedContext = conversationContextService.getContext(email);
                if (savedContext != null) {
                    // 저장된 컨텍스트가 있으면 재사용 (LLM 호출 없음)
                    log.info("Speech Act 감지 - 저장된 컨텍스트 재사용: email={}, intent={}, action={}",
                        email, savedContext.getIntent(), savedContext.getAction());
                    classification = savedContext;
                } else {
                    // 저장된 컨텍스트가 없거나 만료된 경우: 기존 방식으로 폴백
                    log.info("Speech Act 감지 - 컨텍스트 없음, 기존 방식으로 폴백: email={}", email);
                    if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
                        String context = buildContext(request.getConversationHistory());
                        classification = aiIntentService.classifyIntentWithContext(context, text);
                    } else {
                        // conversationHistory도 없으면 일반 의도 분석
                        classification = aiIntentService.classifyIntent(text);
                    }
                }
            } else {
                // 이메일이 없으면 기존 방식으로 처리
                log.info("Speech Act 감지 - 이메일 없음, 기존 방식으로 처리");
                if (request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
                    String context = buildContext(request.getConversationHistory());
                    classification = aiIntentService.classifyIntentWithContext(context, text);
                } else {
                    classification = aiIntentService.classifyIntent(text);
                }
            }
        } else if (isTrigger && request.getConversationHistory() != null && !request.getConversationHistory().isEmpty()) {
            // 기존 트리거 키워드 감지 시: 컨텍스트 포함 의도 분석
            String context = buildContext(request.getConversationHistory());
            classification = aiIntentService.classifyIntentWithContext(context, text);
        } else {
            // 일반 의도 분석
            classification = aiIntentService.classifyIntent(text);
        }
        
        // 5. classification이 null인 경우 에러 처리
        if (classification == null) {
            log.error("의도 분류 결과가 null입니다.");
            return createErrorResponse("의도 분류 중 오류가 발생했습니다.");
        }

        // 6. 일반 요청 시 컨텍스트 저장 (Speech Act가 아닌 경우, JWT에서 email 추출)
        if (!isSpeechAct) {
            String email = getEmailIfNeeded();
            if (email != null) {
                conversationContextService.saveContext(email, classification);
                log.debug("일반 요청 - 컨텍스트 저장: email={}, intent={}, action={}",
                    email, classification.getIntent(), classification.getAction());
            }
        }

        String intent = classification.getIntent();
        String intentNorm = intent == null ? "GENERAL_CHAT" : intent.trim().toUpperCase();

        // [중요] MEAL 멀티턴(pending) 중에는, 사용자의 "짧은 후속 응답"이 의도 분류에 의해 다른 도메인으로 튀면 UX가 망가집니다.
        // 정책: pending이 있는 동안에는 '명확한' WORKOUT/PAIN_REPORT가 아니면 MEAL로 유지합니다.
        Long memberId = null;
        try {
            memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        } catch (Exception ignored) {
            memberId = null;
        }

        MealAiContextDto preCtx = null;
        try {
            if (memberId != null) {
                preCtx = mealAiContextService.get(memberId);
            }
        } catch (Exception ignored) {
            preCtx = null;
        }

        boolean hasMealPending = preCtx != null
                && preCtx.getPending() != null
                && preCtx.getPending().getType() != null
                && !preCtx.getPending().getType().isBlank();

        if (hasMealPending && !"MEAL_QUERY".equals(intentNorm)) {
            boolean clearlyPain = _looksLikePain(text);
            boolean clearlyWorkout = _looksLikeWorkout(text);
            boolean shouldKeepMeal = !(clearlyPain || clearlyWorkout);
            if (shouldKeepMeal) {
                // pending이 있는 동안에는 식단 컨텍스트로 처리
                AIChatResponse forcedMeal = mealChatService.handleMeal(classification, request);
                return forcedMeal;
            }
        }

        // 7. 의도에 따라 적절한 Service 호출
        AIChatResponse response = switch (intentNorm) {
            case "PAIN_REPORT" -> painReportChatService.handlePainReport(classification);
            case "GENERAL_CHAT" -> generalChatService.handleGeneralChat(classification);
            case "WORKOUT" -> workoutChatService.handleWorkout(classification);
            case "MEAL_QUERY" -> mealChatService.handleMeal(classification, request);
            case "BODY_QUERY" -> bodyChatService.handleBodyQuery(classification);
            case "DELIVERY_QUERY" -> deliveryChatService.handleDelivery(classification);
            case "PRODUCT_RECOMMEND" -> commerceChatService.handleCommerceRecommend(classification, request.getText());
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
                // 이미지를 base64로 변환하여 MealServiceImpl.asyncVisionAnalysis 호출
                try {
                    byte[] imageBytes = image.getBytes();
                    String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                    Long userId = null;
                    try {
                        userId = currentMemberService.getCurrentMemberOrThrow().getId();
                    } catch (Exception ignored) {
                        // userId를 가져올 수 없으면 일반 분석으로 fallback
                        return foodAnalysisClient.analyzeFood(image);
                    }
                    // 비동기로 이미지 분석 시작 (결과는 WebSocket으로 전송됨)
                    mealService.asyncVisionAnalysis(userId, base64Image);
                    // 즉시 응답 반환 (분석은 백그라운드에서 진행)
                    return AIChatResponse.builder()
                            .message("이미지 분석을 시작했어요. 잠시만 기다려주세요...")
                            .intent("MEAL_QUERY")
                            .build();
                } catch (Exception e) {
                    log.error("이미지 base64 변환 실패, 일반 분석으로 fallback", e);
                    return foodAnalysisClient.analyzeFood(image);
                }
            }
            
        } catch (Exception e) {
            log.error("이미지 분석 실패", e);
            return createErrorResponse("이미지 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * Speech Act 키워드 감지 (단순 반응형 발화)
     */
    private boolean isSpeechActMessage(String text) {
        if (text == null) {
            return false;
        }

        String trimmedText = text.trim();
        String lowerText = trimmedText.toLowerCase();

        return SPEECH_ACT_KEYWORDS.stream()
            .anyMatch(keyword -> {
                String lowerKeyword = keyword.toLowerCase();
                return lowerText.equals(lowerKeyword) ||
                       lowerText.startsWith(lowerKeyword + " ");
            });
    }

    /**
     * 기존 트리거 키워드 감지 (이전 대화 지시, 이어서, 반복 등)
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
     * JWT claims에서 email을 추출합니다.
     * DB 조회 없이 SecurityContext에서 직접 email을 가져옵니다.
     *
     * @return 사용자 이메일, 조회 실패 시 null
     */
    private String getEmailIfNeeded() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            // SecurityContext의 Principal은 email입니다 (JWTCheckFilter에서 설정)
            Object principal = authentication.getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }

            return null;
        } catch (Exception e) {
            log.debug("JWT에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
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

    /**
     * [도메인 보호] 통증/부상으로 강하게 보이는지(명확할 때만 PAIN_REPORT로 전환)
     */
    private boolean _looksLikePain(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        // 최소 키워드 기반(보수적으로): 통증 관련 단어가 명시될 때만 true
        String[] keywords = new String[] { "아파", "통증", "뻐근", "쑤셔", "저려", "다쳤", "부상", "삐끗", "염좌" };
        for (String k : keywords) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    /**
     * [도메인 보호] 운동 루틴/기록으로 강하게 보이는지(명확할 때만 WORKOUT로 전환)
     * - "운동" 단독 키워드는 식단 대화에서도 등장할 수 있어, 더 구체적인 신호를 우선 사용합니다.
     */
    private boolean _looksLikeWorkout(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;

        String[] strong = new String[] {
                "루틴", "세트", "횟수", "kg", "RM", "운동 추천", "루틴 추천",
                "스쿼트", "벤치", "데드", "데드리프트", "오버헤드", "프레스", "바벨", "플랭크",
                "유산소", "러닝", "런닝", "달리기"
        };
        for (String k : strong) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    private AIChatResponse createErrorResponse(String errorMessage) {
        return AIChatResponse.builder()
            .message(errorMessage)
            .intent("ERROR")
            .build();
    }
}

