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
}
