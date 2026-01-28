package com.backend.config.meal;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;

/**
 * [AI 서버 통신 설정 - WebClient]
 * Feign 제거 후 WebClient 기반 비동기 처리 지원용 설정.
 * - connectTimeout: 10초
 * - responseTimeout: 3분
 */
@Configuration
public class AiMealWebClientConfig {

    @Bean(name = "aiMealHttpClient")
    public WebClient aiMealHttpClient(@Value("${ai.server.url}") @NonNull String baseUrl) {
        String safeBaseUrl = Objects.requireNonNull(baseUrl, "ai.server.url은 필수입니다.");
        HttpClient httpClient = Objects.requireNonNull(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(180))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(180))
                        .addHandlerLast(new WriteTimeoutHandler(180))), "HttpClient 생성 실패");

        return WebClient.builder()
                .baseUrl(safeBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

