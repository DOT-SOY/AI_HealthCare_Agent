package com.backend.controller.ocr;

import com.backend.service.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Double>> analyzeImage(@RequestParam("file") MultipartFile file) {
        Map<String, Double> result = ocrService.analyzeInbodyImage(file);
        return ResponseEntity.ok(result);
    }


    // type: "gpt", "google", "paddle", "easy" 중 하나 선택
    @PostMapping("/{type}")
    public ResponseEntity<Map<String, Object>> analyzeByType(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> result;

        switch (type) {
            case "gpt":
                result = ocrService.ocrByGpt(file);
                break;
            case "google":
                result = ocrService.ocrByGoogle(file);
                break;
            case "paddle":
                result = ocrService.ocrByPython(file, "paddle"); // 파이썬 서버로 토스
                break;
            case "easy":
                result = ocrService.ocrByPython(file, "easy");   // 파이썬 서버로 토스
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 OCR 타입입니다.");
        }
        return ResponseEntity.ok(result);
    }
}