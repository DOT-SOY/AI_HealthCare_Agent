package com.backend.controller.ranking;

import com.backend.dto.response.RankingResponse;
import com.backend.service.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<RankingResponse> getRanking(
            @AuthenticationPrincipal String email,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        log.info("Ranking request - email: {}, limit: {}", email, limit);

        RankingResponse response = rankingService.getRanking(email, limit);

        return ResponseEntity.ok(response);
    }
}

