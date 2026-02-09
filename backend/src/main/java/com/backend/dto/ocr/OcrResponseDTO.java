package com.backend.dto.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OCR API 응답 (파싱된 체성분 + 언어).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrResponseDTO {

    private OcrParsedBodyDTO parsed;
    private String language;
}
