package com.backend.controller.meal;

import com.backend.dto.meal.MealCalendarDto;
import com.backend.dto.meal.MealTargetDto;
import com.backend.domain.member.Member;
import com.backend.repository.member.MemberRepository;
import com.backend.service.meal.MealTargetService;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/meal/target")
@RequiredArgsConstructor
public class MealTargetController {

    private final MealTargetService mealTargetService;
    private final MemberRepository memberRepository;

    /**
     * [조회] 일일 영양 목표 및 달성 피드백
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MealTargetDto>> getDailyTarget(
            @AuthenticationPrincipal String email,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        log.info("[API GET] Target Info - User: {}, Date: {}", email, date);
        Long userId = resolveUserId(email);
        MealTargetDto result = mealTargetService.getTargetByDate(userId, date);
        
        return ResponseEntity.ok(ApiResponse.success(result, "목표 조회가 완료되었습니다."));
    }

    /**
     * [설정] 일일 영양 목표 업데이트 (AI 가이드 혹은 수동 설정)
     */
    @PutMapping
    public ResponseEntity<ApiResponse<MealTargetDto>> setDailyTarget(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MealTargetDto targetDto) {
        
        log.info("[API PUT] Update Target - User: {}", email);
        Long userId = resolveUserId(email);
        MealTargetDto updated = mealTargetService.updateTarget(userId, targetDto);
        
        return ResponseEntity.ok(ApiResponse.success(updated, "목표가 성공적으로 저장되었습니다."));
    }

    /**
     * [캘린더] 월간 성취도 통합 데이터 조회 (O/X/85% 통계)
     * - 그림 시안의 모든 데이터를 단 한 번의 요청으로 반환
     */
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<MealCalendarDto>>> getMonthlyCalendar(
            @AuthenticationPrincipal String email,
            @RequestParam("yearMonth")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate yearMonth) {
        
        log.info("[API GET] Monthly Calendar Stats - User: {}, Month: {}", email, yearMonth);
        Long userId = resolveUserId(email);
        List<MealCalendarDto> result = mealTargetService.getMonthlyCalendarStatus(userId, yearMonth);
        
        return ResponseEntity.ok(ApiResponse.success(result, "월간 성취도 집계가 완료되었습니다."));
    }

    // --- [엔터프라이즈 공통 응답 규격] ---
    @Getter
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data, String message) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }
    }

    private Long resolveUserId(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("인증된 사용자 이메일이 없습니다.");
        }
        return memberRepository.findByEmail(email)
                .filter(m -> !m.isDeleted())
                .map(Member::getId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }
}