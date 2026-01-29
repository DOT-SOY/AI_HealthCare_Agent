package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseAddRequest {
    private String name;
    private String category; // "CHEST", "BACK" 등
    private Integer sets;
    private Integer reps;
    private Double weight;
}
