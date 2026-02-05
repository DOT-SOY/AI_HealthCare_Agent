package com.backend.dto.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingResponseDto {
    private String myPurpose;
    private Integer myRankInGroup;
    private Integer myGroupSize;
    private Double myRoutineRate;
    private Double myMealRate;
    private Double myCombinedRate;
    private Map<String, RankingGroupDto> groups;
}
