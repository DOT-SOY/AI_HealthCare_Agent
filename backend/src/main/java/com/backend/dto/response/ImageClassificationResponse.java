package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이미지 분류 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageClassificationResponse {
    /**
     * 이미지 타입 ("inbody", "food", "unknown")
     */
    private String type;
    
    /**
     * 유사도 점수 (0.0 ~ 1.0)
     */
    private Double confidence;
    
    /**
     * 가장 가까운 벡터 ID
     */
    private String nearestPointId;
    
    /**
     * 에러 메시지 (선택적)
     */
    private String error;
}

