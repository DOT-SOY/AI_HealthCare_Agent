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
}