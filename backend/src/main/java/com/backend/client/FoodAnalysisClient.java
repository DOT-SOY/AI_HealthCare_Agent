package com.backend.client;

import com.backend.dto.response.AIChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 음식 분석 클라이언트
 * Python AI 서버의 /food/analyze 엔드포인트를 호출합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FoodAnalysisClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * 음식 사진을 분석합니다.
     * 
     * @param imageFile 업로드할 이미지 파일
     * @return 음식 분석 결과
     */
    public AIChatResponse analyzeFood(MultipartFile imageFile) {
        log.info("음식 분석 요청: filename={}, size={} bytes", 
            imageFile.getOriginalFilename(), imageFile.getSize());
        
        return baseAIClient.postMultipartRequest(
            "/food/analyze", 
            imageFile, 
            AIChatResponse.class
        );
    }
}

