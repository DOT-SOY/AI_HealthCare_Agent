package com.backend.dto.meal;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [Meal AI 컨텍스트]
 * - 식단 도메인에서 "대화형" 자연어 처리를 위해 최소 컨텍스트를 Redis에 저장합니다.
 * - 저장 대상은 식단 도메인에 한정(전역 채팅 전체 저장 금지).
 *
 * 저장 정책:
 * - 최근 3턴(왕복 3번) = 메시지 6개(user/assistant)까지만 유지
 * - TTL 30분(자동 만료)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealAiContextDto {

    /**
     * role: "user" | "assistant"
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private String content;
        private Instant at;
    }

    /**
     * pending: 직전 턴에서 시스템이 사용자에게 요청한 "대기 중 질문/선택" 상태
     * - 예: 겹침 기간에서 덮어쓰기/빈날만 선택을 기다리는 상태
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Pending {
        private String type; // e.g. OVERLAP_STRATEGY
        @Builder.Default
        private Map<String, Object> data = new HashMap<>();
        private Instant at;
    }

    @Builder.Default
    private List<Message> history = new ArrayList<>();

    private Pending pending;

    /**
     * 서버가 컨텍스트를 마지막으로 갱신한 시간(관측성/디버그용)
     */
    private Instant updatedAt;
}



