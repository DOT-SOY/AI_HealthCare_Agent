package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 통증 수정 모달에서 사용자가 선택한 대체 운동 적용 요청
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PainModifyApplyRequest {
    private LocalDate date;
    /** 바꿀 운동만 포함. exerciseId + 선택한 대체 운동명(selectedName). selectedName이 null/빈값이면 해당 운동 유지 */
    private List<ReplacementItem> replacements;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplacementItem {
        private Long exerciseId;
        private String selectedName; // 대체 운동명. 비우면 기존 유지
    }
}
