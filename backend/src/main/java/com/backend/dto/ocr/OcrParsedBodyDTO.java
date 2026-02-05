package com.backend.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrParsedBodyDTO {
    private Double weight;
    private Double height;
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;

    /** 이미지에 있는 측정일/검사일 (YYYY-MM-DD, OCR 추출 시) */
    private String measurementDate;
}
