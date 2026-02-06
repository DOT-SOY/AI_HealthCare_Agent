package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * PRODUCT_RECOMMEND 의도 처리 서비스
 */
public interface CommerceChatService {
    /**
     * PRODUCT_RECOMMEND 의도 처리
     *
     * 처리 방식: ai-server의 /commerce/recommend 엔드포인트 호출
     * - 사용자 발화와 세션 ID를 전달
     * - 상태머신 기반 대화 플로우 처리
     * - 상품 추천 → 확인 → 장바구니 → 배송지 → 결제 플로우
     */
    AIChatResponse handleCommerceRecommend(IntentClassificationResult classification);

    /**
     * PRODUCT_RECOMMEND 의도 처리 (사용자 발화 텍스트 명시 전달)
     */
    AIChatResponse handleCommerceRecommend(IntentClassificationResult classification, String userText);

    /**
     * 상품 추천 플로우 중인지 확인 (ai-server /commerce/session/check, Redis 세션 키 존재 = SSOT).
     */
    boolean isInCommerceFlow(String sessionId);

    /**
     * 세션 연속 요청 처리 (의도 분류 없이 sessionId와 userText만으로 commerce 플로우 진행).
     * 오케스트레이션 가드에서 in_flow일 때 호출.
     *
     * @param sessionId commerce 세션 ID (예: commerce_{memberId})
     * @param userText  사용자 발화
     * @return ai-server 응답 기반 AIChatResponse (error SESSION_EXPIRED 시 그대로 전달)
     */
    AIChatResponse handleCommerceRecommendBySession(String sessionId, String userText);
}

