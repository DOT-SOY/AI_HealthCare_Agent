package com.backend.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BaseAIClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${ai.server.base-url}")
    private String aiServerBaseUrl;
    
    /**
     * AI 서버에 POST 요청을 보냅니다.
     * 
     * @param endpoint 엔드포인트 경로 (예: "/chat", "/pain/advice")
     * @param requestBody 요청 본문
     * @param responseType 응답 타입
     * @return 응답 객체
     * @param <T> 응답 타입
     */
    public <T> T postRequest(String endpoint, Map<String, Object> requestBody, Class<T> responseType) {
        try {
            String url = aiServerBaseUrl + endpoint;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<T> response = restTemplate.postForEntity(
                url,
                request,
                responseType
            );
            
            return response.getBody();
        } catch (Exception e) {
            log.error("AI 서버 호출 실패 [endpoint: {}]: {}", endpoint, e.getMessage(), e);
            throw new RuntimeException("AI 서버 통신 실패: " + endpoint, e);
        }
    }
    
    /**
     * AI 서버의 base URL을 반환합니다.
     * 
     * @return base URL
     */
    public String getBaseUrl() {
        return aiServerBaseUrl;
    }
}


