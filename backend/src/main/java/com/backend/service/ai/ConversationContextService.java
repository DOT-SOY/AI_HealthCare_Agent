package com.backend.service.ai;

import com.backend.dto.response.IntentClassificationResult;

/**
 * 대화 컨텍스트를 인메모리에 저장하고 관리하는 서비스 인터페이스
 * 사용자별로 최근 의도 분석 결과를 저장하며, TTL 20초로 자동 만료됩니다.
 */
public interface ConversationContextService {
    
    /**
     * 사용자별 컨텍스트를 저장합니다.
     * Map의 put() 메서드를 사용하므로, 사용자당 하나의 컨텍스트만 저장됩니다.
     * 기존 컨텍스트가 있으면 자동으로 덮어씁니다.
     * 
     * @param email 사용자 이메일 (JWT claims에서 추출, unique key)
     * @param result 의도 분석 결과
     */
    void saveContext(String email, IntentClassificationResult result);

    /**
     * 사용자별 컨텍스트를 조회합니다.
     * 만료된 컨텍스트는 null을 반환합니다.
     * 
     * @param email 사용자 이메일 (JWT claims에서 추출)
     * @return 저장된 IntentClassificationResult, 없거나 만료된 경우 null
     */
    IntentClassificationResult getContext(String email);
}
