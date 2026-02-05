package com.backend.dto.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingEntryDto {
    private int rank;
    private Long memberId;
    private String memberName;
    private String purpose;
    private Double routineRate;
    private Double mealRate;
    private Double combinedRate;
}
