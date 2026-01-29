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
public class ExerciseResponse {
    private Long id;
    private String name;
    private String mainTarget; // 메인 타겟
    private List<String> subTargets; // 서브 타겟 목록
    private Integer sets;
    private Integer reps;
    private Double weight;
    private Integer orderIndex;
    private boolean completed;
}

