package com.backend.dto.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingGroupDto {
    private String exercisePurpose;
    private int totalCount;
    private List<RankingEntryDto> top3;
    private List<RankingEntryDto> fullList;
}
