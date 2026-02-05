package com.backend.service.ranking;

import com.backend.dto.ranking.RankingResponseDto;

import java.time.LocalDate;

public interface RankingService {
    RankingResponseDto getRankingByPurpose(String currentUserEmail, Integer periodDays, LocalDate startDate, LocalDate endDate);
}
