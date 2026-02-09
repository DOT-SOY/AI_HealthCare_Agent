package com.backend.dto.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OCR로 추출한 체성분 데이터 (인바디 등).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrParsedBodyDTO {

    private Double weight;
    private Double height;
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;
    private String measurementDate;
}
