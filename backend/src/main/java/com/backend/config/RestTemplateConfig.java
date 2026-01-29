package com.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 타임아웃을 명시적으로 설정하여 AI 서버 지연/다운 시 무한 대기를 방지
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 연결 타임아웃 5초
        factory.setReadTimeout(30000);   // 읽기 타임아웃 30초 (AI 응답이 다소 길 수 있음)
        
        return builder
            .requestFactory(() -> factory)
            .build();
    }
}

