package com.backend.dto.meal;

import lombok.*;
import java.util.List;

/**
 * [AI 응답 통합 DTO]
 * Python 서버가 Java로 보내주는 모든 응답 데이터를 담습니다.
 * 요청 타입에 따라 채워지는 필드가 다릅니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMealResponseDto {

    // [1] 식단 생성/재분배 결과 (GENERATE, REPLAN 응답)
    // AI가 짜준 식단 리스트 (아침, 점심, 저녁...)
    private List<MealDto> suggestedMeals;

    // [2] Vision AI 분석 결과 (ANALYZE_IMAGE 응답)
    // 사진 보고 "이거 치킨이네요" 라고 알려주는 데이터
    private AnalyzedFood analyzedFood;

    // [3] 심층 상담 결과 (ADVICE 응답)
    // "라면은 염분이 많아..." 같은 긴 텍스트
    private String adviceComment;


    // =================================================================
    // [Inner Class] Vision 분석 상세 데이터
    // =================================================================
    @Getter @AllArgsConstructor @Builder
    public static class AnalyzedFood {
        private String foodName;      // 추론된 음식명 (예: "양념치킨")
        private Integer calories;     // 추론된 칼로리
        private Integer carbs;
        private Integer protein;
        private Integer fat;
        
        // (선택) AI가 얼마나 확신하는지?
        // private Double confidence; // 예: 0.98 (98% 확신)
    }
}