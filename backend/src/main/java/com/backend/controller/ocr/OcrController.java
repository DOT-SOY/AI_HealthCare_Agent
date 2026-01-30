package com.backend.controller.ocr;

import com.backend.dto.ocr.OcrResponseDTO;
import com.backend.service.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR API 컨트롤러
 * 이미지 업로드 시 텍스트 추출 결과를 반환합니다. (AI 서버 OCR 연동)
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    /**
     * 이미지 파일에서 텍스트 추출 (OCR)
     *
     * <p>multipart/form-data로 이미지 파일을 전송하면 AI 서버 OCR API를 호출하여
     * 추출된 텍스트를 반환합니다.
     *
     * <p>지원 형식: JPEG, PNG, GIF, WebP, BMP (최대 10MB)
     *
     * @param file 이미지 파일 (multipart key: file)
     * @return 추출된 텍스트 및 메타정보 (text, language, confidence)
     */
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResponseDTO> extractText(@RequestParam("file") MultipartFile file) {
        log.info("OCR 요청: filename={}, size={} bytes", file.getOriginalFilename(), file.getSize());
        OcrResponseDTO response = ocrService.extractText(file);
        return ResponseEntity.ok(response);
    }
}
