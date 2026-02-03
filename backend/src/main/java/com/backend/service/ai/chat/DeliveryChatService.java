package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * DELIVERY_QUERY 의도 처리 서비스
 */
public interface DeliveryChatService {
    /**
     * DELIVERY_QUERY 의도 처리 (대분류: intent)
     *
     * action(소분류)에 따라 분기:
     * - QUERY: 배송 현황 조회
     * - RECOMMEND: 배송 추천 (추후 구현)
     * - MODIFY: 배송 수정 (추후 구현)
     */
    AIChatResponse handleDelivery(IntentClassificationResult classification);
}

