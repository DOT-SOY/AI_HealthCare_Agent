package com.backend.service.ocr;

import com.backend.dto.ocr.OcrResponseDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 서비스 인터페이스
 * 이미지에서 텍스트를 추출합니다. (AI 서버 OCR API 연동)
 */
public interface OcrService {

    /**
     * 이미지 파일에서 텍스트를 추출합니다.
     *
     * @param file 이미지 파일 (JPEG, PNG 등)
     * @return 추출된 텍스트 및 메타정보
     */
    OcrResponseDTO extractText(MultipartFile file);
}
