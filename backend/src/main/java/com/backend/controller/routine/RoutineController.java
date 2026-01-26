package com.backend.controller.routine;

import com.backend.domain.member.Member;
import com.backend.domain.member.Target;
import com.backend.domain.routine.Routine;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.request.RoutineUpdateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.routine.RoutineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
@Slf4j
public class RoutineController {
    
    private final RoutineService routineService;
    private final MemberRepository memberRepository;
    private final RoutineRepository routineRepository;
    
    /**
     * 현재 멤버 ID를 가져오거나 기본 멤버를 생성
     * 더미데이터가 있는 멤버 8을 우선 사용
     */
    @Transactional
    private Long getCurrentMemberId() {
        List<Member> allMembers = memberRepository.findAll();
        log.info("전체 멤버 수: {}", allMembers.size());
        allMembers.forEach(m -> log.info("멤버: id={}, name={}", m.getId(), m.getName()));
        
        // 1. 멤버 4 또는 8이 있으면 우선 사용 (더미데이터가 있는 멤버)
        Long memberId = allMembers.stream()
            .filter(m -> m.getId() == 4L || m.getId() == 8L)
            .findFirst()
            .map(member -> {
                log.info("더미데이터 멤버 사용: id={}, name={}", member.getId(), member.getName());
                return member.getId();
            })
            .orElseGet(() -> {
                // 2. 멤버 4 또는 8이 없으면 루틴이 있는 멤버를 우선 사용
                try {
                    List<Routine> allRoutines = routineRepository.findAll();
                    if (!allRoutines.isEmpty()) {
                        Long memberIdWithRoutines = allRoutines.stream()
                            .map(r -> r.getMember().getId())
                            .distinct()
                            .findFirst()
                            .orElse(null);
                        if (memberIdWithRoutines != null) {
                            Member memberWithRoutines = memberRepository.findById(memberIdWithRoutines).orElse(null);
                            if (memberWithRoutines != null) {
                                log.info("루틴이 있는 멤버 사용: id={}, name={}", memberWithRoutines.getId(), memberWithRoutines.getName());
                                return memberWithRoutines.getId();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("루틴 조회 중 오류 발생: {}", e.getMessage());
                }
                
                // 3. 루틴이 있는 멤버가 없으면 첫 번째 멤버 사용
                return allMembers.stream()
                    .findFirst()
                    .map(member -> {
                        log.info("첫 번째 멤버 사용: id={}, name={}", member.getId(), member.getName());
                        return member.getId();
                    })
                    .orElseGet(() -> {
                        // 4. 멤버가 없으면 기본 멤버 생성
                        log.warn("멤버가 없어서 새로 생성합니다.");
                        Member newMember = Member.builder()
                            .name("테스트 회원")
                            .target(Target.BULK)
                            .physicalInfo("{\"height\": 175, \"weight\": 70}")
                            .build();
                        Member saved = memberRepository.save(newMember);
                        log.info("새 멤버 생성: id={}, name={}", saved.getId(), saved.getName());
                        return saved.getId();
                    });
            });
        
        log.info("최종 선택된 멤버 ID: {}", memberId);
        return memberId;
    }
    
    @GetMapping("/today")
    public ResponseEntity<RoutineResponse> getTodayRoutine() {
        Long memberId = getCurrentMemberId();
        log.info("오늘의 루틴 조회 요청: memberId={}", memberId);
        RoutineResponse response = routineService.getTodayRoutine(memberId);
        if (response == null) {
            log.warn("오늘의 루틴이 없습니다: memberId={}", memberId);
            return ResponseEntity.ok(null);
        }
        log.info("오늘의 루틴 조회 성공: routineId={}, exercisesCount={}, date={}", 
            response.getId(), 
            response.getExercises() != null ? response.getExercises().size() : 0,
            response.getDate());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/weekly")
    public ResponseEntity<List<RoutineResponse>> getWeeklyRoutines() {
        Long memberId = getCurrentMemberId();
        log.info("주간 루틴 조회 요청: memberId={}", memberId);
        List<RoutineResponse> response = routineService.getWeeklyRoutines(memberId);
        log.info("주간 루틴 조회 성공: routinesCount={}", response != null ? response.size() : 0);
        if (response != null && !response.isEmpty()) {
            response.forEach(r -> log.info("루틴: id={}, date={}, exercisesCount={}", 
                r.getId(), r.getDate(), r.getExercises() != null ? r.getExercises().size() : 0));
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 운동 기록 조회
     * - 과거 루틴 목록 조회 (최근 3개월)
     * - bodyPart로 필터링 가능
     */
    @GetMapping("/history")
    public ResponseEntity<List<RoutineResponse>> getHistory(@RequestParam(required = false) String bodyPart) {
        // TODO: 실제 memberId는 인증에서 가져와야 함
        Long memberId = getCurrentMemberId();
        List<RoutineResponse> response = routineService.getHistory(memberId, bodyPart);
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
        Long memberId = getCurrentMemberId();
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
