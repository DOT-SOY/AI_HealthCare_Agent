package com.backend.dto.meal;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealCalendarDto {

    private LocalDate mealDate;          // 날짜 (26일)

    // --- [1] 상단: 메뉴 리스트 (그림 속 텍스트) ---
    // 예: ["아침 달걀볶음밥 1인분", "점심 삼겹살 600g", "저녁 라면 2개..."]
    // 쿼리 결과로 바로 못 가져오고, Service에서 문자열로 만들어 넣어줘야 함.
    private List<String> simpleMealInfoList; 

    // --- [2] 하단: O O X 아이콘 데이터 ---
    private Boolean isCarbsSuccess;      // 탄수화물 성공(O) 여부
    private Boolean isProteinSuccess;    // 단백질 성공(O) 여부
    private Boolean isFatSuccess;        // 지방 성공(X) 여부

    // --- [3] 하단: 종합 퍼센트 (85%) ---
    private Integer achievementRate;     // 종합 달성률 (85)

    // --- [4] 데이터 분석용 기본 필드 ---
    private Integer totalEatenCalories;    // 총 섭취 칼로리
    private Integer totalOriginalCalories; // 계획 대비 분석용
    private Long eatenCount;
    private Long skippedCount;

    // (기타 필요 시 사용)
    private Integer goalCalories;        
    
    // 이 DTO는 필드가 많아서 생성자보다는 Builder나 Setter로 값 채우는 게 필수입니다.
}