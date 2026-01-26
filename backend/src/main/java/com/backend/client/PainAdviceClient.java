package com.backend.client;

import com.backend.dto.response.PainAdviceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PainAdviceClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * Python AI 서버의 /pain/advice 엔드포인트를 호출하여 통증 조언을 받습니다.
     * RAG를 통해 운동별 자극 부위 및 각 부위별 통증 대처 방법을 제공합니다.
     * 
     * @param bodyPart 통증 부위
     * @param count 최근 7일 내 통증 횟수
     * @param description 통증 설명
     * @return 통증 조언 응답
     */
    public PainAdviceResponse requestAdvice(String bodyPart, long count, String description) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("body_part", bodyPart); // Python AI 서버는 body_part를 요구
        requestBody.put("count", (int) count); // int 타입으로 변환
        requestBody.put("note", description); // Python AI 서버는 note를 요구
        
        return baseAIClient.postRequest("/pain/advice", requestBody, PainAdviceResponse.class);
    }
}
