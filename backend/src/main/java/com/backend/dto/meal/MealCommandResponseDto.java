package com.backend.dto.meal;

import lombok.*;

/**
 * [Meal Command 응답 DTO]
 * ai-server /api/meal/command 의 결과(JSON)를 매핑합니다.
 *
 * 엔터프라이즈 원칙:
 * - 백엔드는 이 DTO를 '결정 결과'로 취급하고, DB 작업/비동기 작업을 수행합니다.
 * - 실패 시에도 ai-server는 ASK_CLARIFY 형태로 복구하도록 설계되어 있습니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealCommandResponseDto {
    private String operation; // GENERATE | GENERATE_OVERWRITE | GENERATE_FILL_MISSING | REPLAN | VISION_* | *TOGGLE | ASK_CLARIFY
    private String startDate; // YYYY-MM-DD
    private Integer periodDays; // 1..90
    private String goalType; // DIET | BULK_UP | MAINTAIN
    private String targetDate; // YYYY-MM-DD (REPLAN 대상)
    private String mealTime; // BREAKFAST | LUNCH | DINNER (끼니 단위 토글)
    private String foodName; // 음식명 (항목 단위 토글)
    private Boolean alsoReplan; // 생략/완료 처리 후 재정비까지 원하면 true
    private String clarifyingQuestion; // ASK_CLARIFY 시 사용자에게 질문
    private Double confidence; // 0..1 (옵션)
}







