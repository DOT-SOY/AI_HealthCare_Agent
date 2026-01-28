package com.backend.client.meal;

import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.AiMealResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 * [AI 서버 통신 - WebClient]
 * Feign 제거 후 WebClient 기반으로 AI 서버 호출을 수행합니다.
 */
@Component
public class AiMealWebClient {

    private final WebClient aiMealWebClient;

    public AiMealWebClient(@Qualifier("aiMealHttpClient") WebClient aiMealWebClient) {
        this.aiMealWebClient = aiMealWebClient;
    }

    public AiMealResponseDto sendRequest(AiMealRequestDto requestDto) {
        Objects.requireNonNull(requestDto, "requestDto는 필수입니다.");
        return aiMealWebClient.post()
                .uri("/api/ai/process")
                .bodyValue(requestDto)
                .retrieve()
                .bodyToMono(AiMealResponseDto.class)
                .block();
    }

    public String checkHealth() {
        return aiMealWebClient.post()
                .uri("/api/ai/health")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

