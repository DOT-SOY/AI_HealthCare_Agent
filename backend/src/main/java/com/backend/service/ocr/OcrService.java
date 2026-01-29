package com.backend.service.ocr;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface OcrService {
    public Map<String, Double> analyzeInbodyImage(MultipartFile file);

    // 1. GPT-4o Vision
    public Map<String, Object> ocrByGpt(MultipartFile file);

    // 2. Google Cloud Vision
    public Map<String, Object> ocrByGoogle(MultipartFile file);

    // 3 & 4. 파이썬 서버 연동 (Paddle & EasyOCR)
    public Map<String, Object> ocrByPython(MultipartFile file, String engineType);

}
