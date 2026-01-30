package com.backend.service.ai;

import com.backend.dto.response.IntentClassificationResult;

public interface AIIntentService {
    /**
     * 사용자 입력을 분석하여 의도를 분류합니다.
     * Python AI 서버의 /chat 엔드포인트를 호출하여 LLM 기반 의도 분류를 수행합니다.
     * 
     * @param userInput 사용자 입력 텍스트
     * @return 의도 분류 결과 (intent, entities, aiAnswer, requiresDbCheck)
     */
    IntentClassificationResult classifyIntent(String userInput);
}
