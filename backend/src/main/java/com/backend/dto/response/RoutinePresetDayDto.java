package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutinePresetDayDto {
    private String title;
    private String summary; // AI 코칭 요약 (루틴 저장 시 summary 필드에 사용)
    private List<String> exerciseNames;
}
