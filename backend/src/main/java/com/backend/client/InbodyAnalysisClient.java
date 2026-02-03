package com.backend.client;

import com.backend.dto.response.AIChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 인바디 분석 클라이언트
 * Python AI 서버의 /inbody/analyze 엔드포인트를 호출합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InbodyAnalysisClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * 인바디 사진을 분석합니다.
     * 
     * @param imageFile 업로드할 이미지 파일
     * @return 인바디 분석 결과
     */
    public AIChatResponse analyzeInbody(MultipartFile imageFile) {
        log.info("인바디 분석 요청: filename={}, size={} bytes", 
            imageFile.getOriginalFilename(), imageFile.getSize());
        
        return baseAIClient.postMultipartRequest(
            "/inbody/analyze", 
            imageFile, 
            AIChatResponse.class
        );
    }
}

