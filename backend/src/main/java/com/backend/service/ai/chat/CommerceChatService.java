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
}

