package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingResponse {

    private List<RankingEntry> topRanks;
    private Integer myRank;
    private RankingEntry myScore;
    private Long totalCount;
    private FilterInfo filterInfo;
}


