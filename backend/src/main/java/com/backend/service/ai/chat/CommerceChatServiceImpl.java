package com.backend.service.ai.chat;

import com.backend.client.BaseAIClient;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.member.CurrentMemberService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * PRODUCT_RECOMMEND 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommerceChatServiceImpl implements CommerceChatService {

    private final BaseAIClient baseAIClient;
    private final CurrentMemberService currentMemberService;
    private final RestTemplate restTemplate;

    @Override
    public AIChatResponse handleCommerceRecommend(IntentClassificationResult classification) {
        return handleCommerceRecommend(classification, null);
    }

    @Override
    public AIChatResponse handleCommerceRecommend(IntentClassificationResult classification, String userText) {
        log.info("PRODUCT_RECOMMEND 의도 처리 시작");
        String sessionId = "commerce_" + currentMemberService.getCurrentMemberOrThrow().getId();
        String text = (userText != null && !userText.trim().isEmpty())
            ? userText
            : (classification != null && classification.getAiAnswer() != null ? classification.getAiAnswer() : "");
        return callCommerceRecommend(sessionId, text);
    }
    
    @Override
    public boolean isInCommerceFlow(String sessionId) {
        try {
            // ai-server의 /commerce/session/check 엔드포인트 호출
            String url = baseAIClient.getBaseUrl() + "/commerce/session/check";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
                Map<String, Object> result = response.getBody();
                if (result != null) {
                    Boolean inFlow = (Boolean) result.get("in_flow");
                    String state = (String) result.get("state");
                    log.info("commerce 세션 확인: sessionId={}, in_flow={}, state={}", sessionId, inFlow, state);
                    return Boolean.TRUE.equals(inFlow);
                }
            } catch (Exception e) {
                log.debug("세션 상태 확인 실패 (플로우 중이 아닌 것으로 간주): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("세션 상태 확인 중 오류: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public AIChatResponse handleCommerceRecommendBySession(String sessionId, String userText) {
        log.info("PRODUCT_RECOMMEND 세션 연속 처리: sessionId={}", sessionId);
        String text = (userText != null && !userText.trim().isEmpty()) ? userText : "";
        return callCommerceRecommend(sessionId, text);
    }

    /**
     * ai-server /commerce/recommend 호출 공통 로직.
     * SESSION_EXPIRED 등 error 필드는 data에 그대로 담아 클라이언트에 전달.
     */
    private AIChatResponse callCommerceRecommend(String sessionId, String text) {
        try {
            String url = baseAIClient.getBaseUrl() + "/commerce/recommend";
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("session_id", sessionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest httpRequest = attributes.getRequest();
                String authHeader = httpRequest != null ? httpRequest.getHeader("Authorization") : null;
                if (authHeader != null && !authHeader.trim().isEmpty()) {
                    headers.set("Authorization", authHeader);
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> commerceResponse = response.getBody();
            if (commerceResponse == null) {
                return AIChatResponse.builder()
                    .message("상품 추천 처리 중 오류가 발생했습니다.")
                    .intent("PRODUCT_RECOMMEND")
                    .build();
            }

            String message = (String) commerceResponse.get("message");
            if (message == null || message.trim().isEmpty()) {
                message = "상품 추천 처리 중 오류가 발생했습니다.";
            }
            String state = (String) commerceResponse.get("state");
            String error = (String) commerceResponse.get("error");
            Boolean handoffToGeneralChat = (Boolean) commerceResponse.get("handoff_to_general_chat");
            log.info("commerce 응답: sessionId={}, state={}, error={}, handoffToGeneralChat={}", sessionId, state, error, handoffToGeneralChat);

            // 원하는 상품이 없는 질문으로 판단된 경우: 일반 챗 응답으로 넘겨 UI/톤을 일반 대화처럼 표시
            if (Boolean.TRUE.equals(handoffToGeneralChat)) {
                return AIChatResponse.builder()
                    .message(message)
                    .intent("GENERAL_CHAT")
                    .data(commerceResponse)
                    .build();
            }
            return AIChatResponse.builder()
                .message(message)
                .intent("PRODUCT_RECOMMEND")
                .data(commerceResponse)
                .build();
        } catch (Exception e) {
            log.error("PRODUCT_RECOMMEND 호출 실패", e);
            return AIChatResponse.builder()
                .message("상품 추천 처리 중 오류가 발생했습니다. 다시 시도해주세요.")
                .intent("PRODUCT_RECOMMEND")
                .build();
        }
    }
}

