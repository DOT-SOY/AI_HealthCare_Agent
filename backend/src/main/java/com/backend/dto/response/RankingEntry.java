package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingEntry {
    private Long memberId;
    private String nickname;
    private String gender;
    private String ageGroup;
    private String exercisePurpose;
    private double mealScore;
    private double routineScore;
    private double totalScore;
    private int rank;
}


