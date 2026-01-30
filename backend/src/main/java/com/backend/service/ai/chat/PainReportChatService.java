package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;

/**
 * PAIN_REPORT 의도 처리 서비스
 */
public interface PainReportChatService {
    /**
     * PAIN_REPORT 의도 처리
     * 
     * 처리 방식: 의도 분류만 받고, 백엔드에서 Python AI 서버의 특정 함수를 다시 호출
     * - 의도 분류에서 entities 추출 (body_part, intensity)
     * - 백엔드 Service를 통해 통증 DB 저장 및 RAG 기반 조언 요청
     * - Python AI 서버의 /pain/advice 엔드포인트 호출
     * - 오늘 루틴과의 관련성 확인 및 에스컬레이션 처리
     */
    AIChatResponse handlePainReport(IntentClassificationResult classification);
}

