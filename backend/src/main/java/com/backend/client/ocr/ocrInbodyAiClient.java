package com.backend.client.ocr;

import com.backend.client.BaseAIClient;
import com.backend.dto.response.AIChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 인바디 OCR 분석을 위해 AI 서버(Python)를 호출하는 클라이언트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ocrInbodyAiClient {

    private final BaseAIClient baseAIClient;

    /**
     * AI 서버의 /inbody/analyze 호출
     * @return AI 서버의 분석 결과 (data 필드에 OCR 결과 포함 예상)
     */
    public Map<String, Object> callAnalyzeApi(MultipartFile file) {
        log.info("[OCR Client] 인바디 분석 요청 전송: {}", file.getOriginalFilename());
        
        // AIChatResponse 형태로 받아서 data 필드만 추출
        AIChatResponse response = baseAIClient.postMultipartRequest(
                "/inbody/analyze",
                file,
                AIChatResponse.class
        );
        
        if (response == null || response.getData() == null) {
            log.warn("[OCR Client] 응답이 비어있음");
            return Map.of();
        }
        
        try {
            // data 필드가 Map 형태라고 가정
            return (Map<String, Object>) response.getData();
        } catch (Exception e) {
            log.error("[OCR Client] 데이터 파싱 실패", e);
            return Map.of();
        }
    }
}




