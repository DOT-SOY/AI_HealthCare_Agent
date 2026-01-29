package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseUpdateRequest {
    private String name;
    private String category;
    private Integer sets;
    private Integer reps;
    private Double weight;
}

