package com.backend.controller.meal;

import com.backend.dto.meal.MealDto;
import com.backend.domain.member.Member;
import com.backend.repository.member.MemberRepository;
import com.backend.service.meal.MealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * [식단 관리 도메인 엔터프라이즈 컨트롤러]
 * 시큐리티 인증 기반의 식단 관리 및 비동기 AI 분석 기능을 총괄합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/meal") // 버전 관리 포함
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;
    private final MemberRepository memberRepository;

    /**
     * [조회] 대시보드 통합 데이터 (식사 탭 & 캘린더 모달 공용)
     * 시큐리티 필터에서 검증된 회원 정보를 기반으로 해당 날짜의 모든 지표를 반환합니다.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @AuthenticationPrincipal String email, 
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        Long userId = resolveUserId(email); // 이메일을 통한 내부 ID 식별
        
        log.info("[API GET] Dashboard - User: {}, Date: {}", email, targetDate);
        return ResponseEntity.ok(mealService.getMealDashboard(userId, targetDate));
    }

    /**
     * [생성] 계획 외 추가 식단 등록 (Confirm AI Result or Manual Add)
     */
    @PostMapping("/intake")
    public ResponseEntity<MealDto> recordIntake(
            @AuthenticationPrincipal String email,
            @RequestBody MealDto mealDto) {
        
        Long userId = resolveUserId(email);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mealService.registerAdditionalMeal(userId, mealDto));
    }

    /**
     * [수정] 식단 상세 내역 수정 (음식명, 영양소, 양 등)
     */
    @PutMapping("/intake/{scheduleId}")
    public ResponseEntity<MealDto> updateIntake(
            @PathVariable Long scheduleId,
            @RequestBody MealDto mealDto) {
        
        return ResponseEntity.ok(mealService.updateMeal(scheduleId, mealDto));
    }

    /**
     * [상태 변경] 식사 완료(EATEN) 상태 토글
     */
    @PatchMapping("/intake/{scheduleId}/status")
    public ResponseEntity<Void> toggleIntakeStatus(
            @PathVariable Long scheduleId,
            @RequestParam("status") String status) {
        
        mealService.toggleMealStatus(scheduleId, status);
        return ResponseEntity.noContent().build();
    }

    /**
     * [삭제/생략] 식단 영구 삭제 또는 SKIPPED 처리
     */
    @DeleteMapping("/intake/{scheduleId}")
    public ResponseEntity<Void> removeIntake(
            @PathVariable Long scheduleId,
            @RequestParam(value = "isPermanent", defaultValue = "false") boolean isPermanent) {
        
        mealService.removeOrSkipMeal(scheduleId, isPermanent);
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    // [AI Async API] - WebSocket 연동용 비동기 창구
    // =================================================================

    /**
     * [Vision] 이미지 분석 요청 (Vision AI)
     * 전역 모달창에서 이미지 업로드 시 호출.
     */
    @PostMapping("/vision/analyze")
    public ResponseEntity<Map<String, Object>> analyzeVision(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> requestData) {
        
        Long userId = resolveUserId(email);
        mealService.asyncVisionAnalysis(userId, requestData.get("image"));
        
        return ResponseEntity.accepted().body(Map.of(
            "status", "ACCEPTED",
            "message", "이미지 분석 작업이 큐에 등록되었습니다.",
            "targetChannel", "/topic/meal/vision/" + userId
        ));
    }

    /**
     * [Advice] 심층 영양 상담 요청 (탭 2)
     */
    @PostMapping("/ai/advice")
    public ResponseEntity<Map<String, Object>> requestAdvice(
            @AuthenticationPrincipal String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Long userId = resolveUserId(email);
        mealService.asyncDeepAdvice(userId, date);
        
        return ResponseEntity.accepted().body(Map.of(
            "status", "ACCEPTED",
            "message", "심층 분석 상담을 시작합니다.",
            "targetChannel", "/topic/meal/advice/" + userId
        ));
    }

    /**
     * [Replan] 실시간 식단 재구성 요청 (Skip/Over 발생 시)
     */
    @PostMapping("/ai/replan")
    public ResponseEntity<Map<String, Object>> requestReplan(
            @AuthenticationPrincipal String email,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Long userId = resolveUserId(email);
        mealService.asyncMealReplan(userId, date);
        
        return ResponseEntity.accepted().body(Map.of(
            "status", "ACCEPTED",
            "message", "목표 잔량을 기준으로 식단을 재구성합니다.",
            "targetChannel", "/topic/meal/replan/" + userId
        ));
    }

    /**
     * [인증 헬퍼] 이메일 기반 회원 번호 식별
     */
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

