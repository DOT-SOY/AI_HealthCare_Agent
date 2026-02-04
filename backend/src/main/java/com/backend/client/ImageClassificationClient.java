package com.backend.client;

import com.backend.dto.response.ImageClassificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 분류 클라이언트
 * Python AI 서버의 /image/classify 엔드포인트를 호출합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageClassificationClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * 이미지를 분류하여 인바디인지 음식인지 판단합니다.
     * 
     * @param imageFile 업로드할 이미지 파일
     * @return 이미지 분류 결과
     */
    public ImageClassificationResponse classifyImage(MultipartFile imageFile) {
        log.info("이미지 분류 요청: filename={}, size={} bytes", 
            imageFile.getOriginalFilename(), imageFile.getSize());
        
        return baseAIClient.postMultipartRequest(
            "/image/classify", 
            imageFile, 
            ImageClassificationResponse.class
        );
    }
}

