package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutFeedbackRequest {
    private String exercise_type;
    private Integer total_reps;
    private Integer duration_sec;
    private String main_issue;
    private Double bad_posture_ratio;
}


