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
public class ExerciseResponse {
    private Long id;
    private String name;
    private String category;
    private Integer sets;
    private Integer reps;
    private Double weight;
    private Integer orderIndex;
    private boolean completed;
}

