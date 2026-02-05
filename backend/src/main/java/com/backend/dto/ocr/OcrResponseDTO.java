package com.backend.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OCR 결과 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrResponseDTO {

    /** 추출된 텍스트 */
    private String text;

    /** 인식된 언어 코드 (예: ko, en) */
    private String language;

    /** 인식 신뢰도 0~1 (선택) */
    private Double confidence;

    /** 인바디 체성분 파싱 결과 (백엔드 JSON 추출 시) */
    private OcrParsedBodyDTO parsed;
}
