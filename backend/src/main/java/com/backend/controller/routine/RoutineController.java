package com.backend.controller.routine;

import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.request.RoutineUpdateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.routine.RoutineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
@Slf4j
public class RoutineController {
    
    private final RoutineService routineService;
    private final CurrentMemberService currentMemberService;
    
    @GetMapping("/today")
    public ResponseEntity<RoutineResponse> getTodayRoutine() {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        log.debug("오늘의 루틴 조회 요청: memberId={}", memberId);
        RoutineResponse response = routineService.getTodayRoutine(memberId);
        if (response == null) {
            return ResponseEntity.ok(null);
        }
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/weekly")
    public ResponseEntity<List<RoutineResponse>> getWeeklyRoutines() {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        log.debug("주간 루틴 조회 요청: memberId={}", memberId);
        List<RoutineResponse> response = routineService.getWeeklyRoutines(memberId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 운동 기록 조회
     * - 과거 루틴 목록 조회 (최근 3개월)
     * - bodyPart로 필터링 가능
     */
    @GetMapping("/history")
    public ResponseEntity<List<RoutineResponse>> getHistory(@RequestParam(required = false) String bodyPart) {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        List<RoutineResponse> response = routineService.getHistory(memberId, bodyPart);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 각 운동별로 가장 최신 루틴 1개씩 조회
     * - 기록 페이지 초기 로딩용
     */
    @GetMapping("/history/latest")
    public ResponseEntity<Map<String, RoutineResponse>> getLatestRoutinesByExercise() {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        log.debug("운동별 최신 루틴 조회 요청: memberId={}", memberId);
        Map<String, RoutineResponse> response = routineService.getLatestRoutinesByExercise(memberId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 운동의 루틴 목록을 페이지네이션으로 조회
     * - 무한 스크롤용
     */
    @GetMapping("/history/exercise/{exerciseName}")
    public ResponseEntity<Page<RoutineResponse>> getRoutinesByExercise(
        @PathVariable String exerciseName,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "1") int size
    ) {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        log.debug("특정 운동의 루틴 조회 요청: memberId={}, exerciseName={}, page={}, size={}", 
            memberId, exerciseName, page, size);
        Page<RoutineResponse> response = routineService.getRoutinesByExercise(
            memberId, exerciseName, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * 특정 루틴 상세 조회
     * - 운동 기록 페이지에서 특정 루틴의 상세 정보 조회
     */
    @GetMapping("/{routineId}")
    public ResponseEntity<RoutineResponse> getRoutine(@PathVariable Long routineId) {
        RoutineResponse response = routineService.getRoutineById(routineId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
    
    @PostMapping
    public ResponseEntity<RoutineResponse> createRoutine(@RequestBody RoutineCreateRequest request) {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse response = routineService.createRoutine(memberId, request);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{routineId}/status")
    public ResponseEntity<RoutineResponse> updateRoutineStatus(
        @PathVariable Long routineId,
        @RequestBody RoutineUpdateRequest request
    ) {
        RoutineResponse response = routineService.updateRoutineStatus(routineId, request.getStatus());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{routineId}/exercises")
    public ResponseEntity<ExerciseResponse> addExercise(
        @PathVariable Long routineId,
        @RequestBody ExerciseAddRequest request
    ) {
        ExerciseResponse response = routineService.addExercise(routineId, request);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{routineId}/exercises/{exerciseId}")
    public ResponseEntity<ExerciseResponse> updateExercise(
        @PathVariable Long routineId,
        @PathVariable Long exerciseId,
        @RequestBody ExerciseUpdateRequest request
    ) {
        ExerciseResponse response = routineService.updateExercise(routineId, exerciseId, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{routineId}/exercises/{exerciseId}")
    public ResponseEntity<Void> deleteExercise(
        @PathVariable Long routineId,
        @PathVariable Long exerciseId
    ) {
        routineService.deleteExercise(routineId, exerciseId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{routineId}/exercises/{exerciseId}/toggle")
    public ResponseEntity<ExerciseResponse> toggleExerciseCompleted(
        @PathVariable Long routineId,
        @PathVariable Long exerciseId
    ) {
        ExerciseResponse response = routineService.toggleExerciseCompleted(routineId, exerciseId);
        return ResponseEntity.ok(response);
    }
}
