package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 대화 컨텍스트를 저장하는 클래스
 * 의도 분석 결과(IntentClassificationResult)를 그대로 저장합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {
    private IntentClassificationResult result; // 의도 분석 결과를 그대로 저장
    private LocalDateTime createdAt; // TTL 계산용 (20초)
}

