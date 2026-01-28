package com.backend.client.meal;

import com.backend.config.meal.AiMealConfig;
import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.AiMealResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * [AI 서버 통신 인터페이스]
 * Python AI 서버와 HTTP 통신을 수행합니다.
 * @Async 어노테이션을 Service 계층에서 붙이면 자동으로 비동기 처리가 됩니다.
 * 따라서 Client 코드는 심플하게 유지하는 것이 좋습니다.
 */
@FeignClient(name = "ai-meal-client", url = "${ai.server.url}", configuration = AiMealConfig.class)
public interface AiMealClient {

    /**
     * 통합 AI 요청 (식단 생성, 재분배, Vision 분석, 상담)
     * POST /api/ai/process
     */
    @PostMapping("/api/ai/process")
    AiMealResponseDto sendRequest(@RequestBody AiMealRequestDto requestDto);

    /**
     * [헬스 체크]
     * AI 서버가 살아있는지 확인용 (배치 작업이나 비동기 시작 전 체크)
     */
    @PostMapping("/api/ai/health")
    String checkHealth();
}