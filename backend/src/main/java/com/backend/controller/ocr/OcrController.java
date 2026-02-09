package com.backend.controller.ocr;

import com.backend.client.OpenAiVisionClient;
import com.backend.dto.ocr.OcrResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 인바디/체성분 이미지 OCR API (Vision gpt-4o-mini).
 * 단백질/무기질은 설정된 영역(파란·빨간 박스) 크롭 후 별도 추출하여 정확도를 높입니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OpenAiVisionClient openAiVisionClient;

    @PostMapping("/extract")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OcrResponseDTO> extract(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!openAiVisionClient.isAvailable()) {
            return ResponseEntity.status(503).body(
                    OcrResponseDTO.builder().language("ko").build());
        }
        OcrResponseDTO result = openAiVisionClient.extractText(file);
        if (result == null) {
            return ResponseEntity.status(503).body(OcrResponseDTO.builder().language("ko").build());
        }
        return ResponseEntity.ok(result);
    }
}
