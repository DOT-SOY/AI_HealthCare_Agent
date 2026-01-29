package com.backend.service.ocr;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface OcrService {
    public Map<String, Double> analyzeInbodyImage(MultipartFile file);

}
