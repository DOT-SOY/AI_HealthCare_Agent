package com.backend.controller.ranking;

import com.backend.dto.ranking.RankingResponseDto;
import com.backend.service.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRanking(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false, defaultValue = "30") Integer period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        RankingResponseDto dto = rankingService.getRankingByPurpose(email, period, startDate, endDate);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "랭킹 조회가 완료되었습니다.",
                "data", dto));
    }
}
