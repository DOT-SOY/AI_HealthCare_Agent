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
    
    /**
     * 상품 추천 플로우 중인지 확인
     * @param sessionId 세션 ID
     * @return 상품 추천 플로우 중이면 true
     */
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
                    log.debug("세션 상태 확인: sessionId={}, inFlow={}, state={}", sessionId, inFlow, state);
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
    
    /**
     * 사용자 발화 텍스트를 받는 오버로드 메서드
     */
    public AIChatResponse handleCommerceRecommend(IntentClassificationResult classification, String userText) {
        log.info("PRODUCT_RECOMMEND 의도 처리 시작");
        
        try {
            // 현재 사용자 확인
            Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
            
            // 세션 ID 생성 (멤버 ID 기반으로 고정 - 같은 멤버는 항상 같은 세션 사용)
            // 이렇게 하면 상품 추천 플로우에서 상태가 유지됨
            String sessionId = "commerce_" + memberId;
            
            // 사용자 발화 텍스트가 없으면 aiAnswer 사용 (fallback)
            if (userText == null || userText.trim().isEmpty()) {
                userText = classification.getAiAnswer() != null 
                    ? classification.getAiAnswer() 
                    : "";
            }
            
            // ai-server의 /commerce/recommend 엔드포인트 호출
            // Authorization 헤더가 필요하므로 RestTemplate을 직접 사용
            String url = baseAIClient.getBaseUrl() + "/commerce/recommend";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", userText);
            requestBody.put("session_id", sessionId);
            
            // RestTemplate을 사용하여 Authorization 헤더 추가
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 현재 요청의 Authorization 헤더 가져오기
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest httpRequest = attributes.getRequest();
                String authHeader = httpRequest.getHeader("Authorization");
                if (authHeader != null && !authHeader.trim().isEmpty()) {
                    headers.set("Authorization", authHeader);
                    log.debug("Authorization 헤더를 ai-server로 전달: {}", authHeader.substring(0, Math.min(20, authHeader.length())) + "...");
                } else {
                    log.warn("현재 요청에 Authorization 헤더가 없습니다. ai-server에서 Backend API 호출 시 인증 실패할 수 있습니다.");
                }
            } else {
                log.warn("RequestContextHolder에서 현재 요청을 가져올 수 없습니다.");
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url,
                request,
                Map.class
            );
            
            Map<String, Object> commerceResponse = response.getBody();
            
            // 응답에서 메시지 추출
            String message = (String) commerceResponse.get("message");
            if (message == null || message.trim().isEmpty()) {
                message = "상품 추천 처리 중 오류가 발생했습니다.";
            }
            
            log.info("PRODUCT_RECOMMEND 응답: state={}, messageLength={}", 
                commerceResponse.get("state"), 
                message.length());
            
            return AIChatResponse.builder()
                .message(message)
                .intent("PRODUCT_RECOMMEND")
                .data(commerceResponse)  // 상태머신 상태, 상품 정보 등 추가 데이터
                .build();
                
        } catch (Exception e) {
            log.error("PRODUCT_RECOMMEND 처리 실패", e);
            return AIChatResponse.builder()
                .message("상품 추천 처리 중 오류가 발생했습니다. 다시 시도해주세요.")
                .intent("PRODUCT_RECOMMEND")
                .build();
        }
    }
}

