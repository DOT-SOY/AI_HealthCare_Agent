package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutineResponse {
    private Long id;
    private LocalDate date;
    private String title;
    private String status;
    private boolean isToday;
    private String summary;
    private List<ExerciseResponse> exercises;
}

