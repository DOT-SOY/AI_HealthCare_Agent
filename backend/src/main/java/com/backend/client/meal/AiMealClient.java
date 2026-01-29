package com.backend.client.meal;

import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.AiMealResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * [AI 서버 통신 클라이언트]
 * WebFlux 기반 비동기 HTTP 클라이언트로 Python AI 서버와 통신합니다.
 * 
 * 비동기 처리 방식:
 * - WebClient를 사용하여 논블로킹 방식으로 요청/응답 처리
 * - Reactor의 Mono를 사용하여 비동기 스트림 처리
 * - 재시도 로직 및 타임아웃 설정 포함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMealClient {

    private final WebClient webClient;

    @Value("${ai.server.url:http://localhost:8000}/api/meal")
    private String aiServerUrl;

    /**
     * AI 서버로 비동기 요청을 보내고 CompletableFuture로 응답을 반환합니다.
     * 
     * 변경 이유:
     * - .block() 제거로 진정한 비동기 처리 구현
     * - CompletableFuture 반환으로 @Async 메서드와 자연스러운 통합
     * - WebClient의 Mono를 CompletableFuture로 변환
     * 
     * @param request AI 요청 DTO
     * @return CompletableFuture<AiMealResponseDto> 비동기 응답
     */
    public CompletableFuture<AiMealResponseDto> sendRequestAsync(AiMealRequestDto request) {
        log.info("[AiMealClient] AI 서버 비동기 요청 시작 - Type: {}", request.getRequestType());
        
        // WebClient의 Mono를 CompletableFuture로 변환
        return webClient
                .post()
                .uri(aiServerUrl + "/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiMealResponseDto.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1))
                        .filter(throwable -> {
                            log.warn("[AiMealClient] 재시도 가능한 오류: {}", throwable.getMessage());
                            return throwable instanceof java.util.concurrent.TimeoutException
                                    || throwable instanceof org.springframework.web.reactive.function.client.WebClientException;
                        }))
                .doOnSuccess(response -> {
                    if (response == null) {
                        log.error("[AiMealClient] AI 서버로부터 null 응답 수신");
                    } else {
                        log.info("[AiMealClient] AI 서버 응답 수신 완료");
                    }
                })
                .doOnError(error -> log.error("[AiMealClient] AI 서버 통신 실패: ", error))
                .toFuture() // Mono를 CompletableFuture로 변환
                .exceptionally(throwable -> {
                    log.error("[AiMealClient] AI 서버 통신 중 예외 발생: ", throwable);
                    throw new RuntimeException("AI 서버 통신 실패: " + throwable.getMessage(), throwable);
                });
    }
}

