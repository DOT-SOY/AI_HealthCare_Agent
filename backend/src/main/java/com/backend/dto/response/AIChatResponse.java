package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChatResponse {
    private String message;
    private String intent; // PAIN_REPORT, GENERAL_CHAT 등
    private Object data; // 추가 데이터 (선택적)
    /** true이면 프론트에서 루틴 프리셋 선택 모달 표시 (WORKOUT RECOMMEND 시) */
    private Boolean showPresetModal;
    /** true이면 프론트에서 통증 부위별 대체 운동 선택 모달 표시 (WORKOUT MODIFY 시) */
    private Boolean showReplaceModal;
}
