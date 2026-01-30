package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * BODY_QUERY 의도 처리 서비스
 */
public interface BodyChatService {
    /**
     * BODY_QUERY 의도 처리
     * 
     * - entities에서 date, body_metric 추출
     * - MemberInfoBodyService를 통해 인바디 조회
     * - 조회 결과를 자연어 메시지로 포맷팅
     */
    AIChatResponse handleBodyQuery(IntentClassificationResult classification);
}

