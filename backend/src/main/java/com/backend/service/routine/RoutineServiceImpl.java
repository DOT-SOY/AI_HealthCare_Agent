package com.backend.service.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.exercise.ExerciseType;
import com.backend.domain.member.Member;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.repository.exercise.ExerciseRepository;
import com.backend.repository.exercise.ExerciseTypeRepository;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.WorkoutReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineServiceImpl implements RoutineService {
    
    private final RoutineRepository routineRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseTypeRepository exerciseTypeRepository;
    private final MemberRepository memberRepository;
    private final WorkoutReviewService workoutReviewService;
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getTodayRoutine(Long memberId) {
        LocalDate today = LocalDate.now();
        log.info("오늘의 루틴 조회: memberId={}, date={}", memberId, today);
        
        Routine routine = routineRepository.findByDateAndMemberId(today, memberId)
            .orElse(null);
        
        if (routine == null) {
            log.warn("오늘의 루틴을 찾을 수 없습니다: memberId={}, date={}", memberId, today);
            return null;
        }
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        log.info("오늘의 루틴 조회 성공: routineId={}, exercisesCount={}", 
            routine.getId(), 
            routine.getExercises().size());
        
        return toRoutineResponse(routine, true);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getWeeklyRoutines(Long memberId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        
        log.info("주간 루틴 조회: memberId={}, weekStart={}, today={}", memberId, weekStart, today);
        
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(memberId, weekStart, today);
        
        log.info("주간 루틴 조회 결과: routinesCount={}", routines.size());
        
        return routines.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(today)))
            .sorted(Comparator.comparing(RoutineResponse::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getHistory(Long memberId, String bodyPart) {
        // 완료된 운동이 있는 루틴만 조회
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(
            memberId, 
            LocalDate.now().minusMonths(3), 
            LocalDate.now()
        );
        
        log.info("기록 조회: memberId={}, bodyPart={}, 전체 루틴 수={}", memberId, bodyPart, routines.size());
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        // 완료된 운동이 있는 루틴만 필터링
        List<Routine> routinesWithCompletedExercises = routines.stream()
            .filter(routine -> routine.getExercises().stream()
                .anyMatch(Exercise::isCompleted))
            .collect(Collectors.toList());
        
        // bodyPart 필터링 (메인 타겟 또는 서브 타겟 포함)
        if (bodyPart != null && !bodyPart.isEmpty() && !bodyPart.equals("전체")) {
            ExerciseCategory targetCategory = mapBodyPartToCategory(bodyPart);
            if (targetCategory != null) {
                routinesWithCompletedExercises = routinesWithCompletedExercises.stream()
                    .filter(routine -> routine.getExercises().stream()
                        .anyMatch(ex -> {
                            if (!ex.isCompleted()) return false;
                            // 메인 타겟 또는 서브 타겟에 포함되는지 확인
                            ExerciseType exerciseType = ex.getExerciseType();
                            if (exerciseType != null) {
                                boolean matchesMain = exerciseType.getMainTarget() == targetCategory;
                                boolean matchesSub = exerciseType.getSubTargets() != null 
                                    && exerciseType.getSubTargets().contains(targetCategory);
                                return matchesMain || matchesSub;
                            }
                            // exerciseType이 없으면 매칭되지 않음
                            return false;
                        }))
                    .collect(Collectors.toList());
            }
        }
        
        log.info("완료된 운동이 있는 루틴 수: {}", routinesWithCompletedExercises.size());
        
        return routinesWithCompletedExercises.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(LocalDate.now())))
            .sorted(Comparator.comparing(RoutineResponse::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    private ExerciseCategory mapBodyPartToCategory(String bodyPart) {
        // BodyPartMapper와 동일한 로직
        switch (bodyPart.trim().toLowerCase()) {
            case "어깨": case "shoulder": return ExerciseCategory.SHOULDER;
            case "가슴": case "chest": return ExerciseCategory.CHEST;
            case "등": case "허리": case "back": return ExerciseCategory.BACK;
            case "팔": case "arm": return ExerciseCategory.ARM;
            case "코어": case "core": return ExerciseCategory.CORE;
            case "복근": case "abs": return ExerciseCategory.ABS;
            case "둔근": case "glute": return ExerciseCategory.GLUTE;
            case "허벅지": case "다리": case "무릎": case "thigh": case "leg": case "knee": return ExerciseCategory.THIGH;
            case "종아리": case "calf": return ExerciseCategory.CALF;
            default: return null;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutineById(Long routineId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElse(null);
        
        if (routine == null) {
            return null;
        }
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional
    public RoutineResponse createRoutine(Long memberId, RoutineCreateRequest request) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + memberId));
        
        // 해당 날짜에 이미 루틴이 있는지 확인
        routineRepository.findByDateAndMemberId(request.getDate(), memberId)
            .ifPresent(routine -> {
                throw new IllegalArgumentException("해당 날짜에 이미 루틴이 존재합니다.");
            });
        
        Routine routine = Routine.builder()
            .member(member)
            .date(request.getDate())
            .title(request.getTitle() != null ? request.getTitle() : "새로운 루틴")
            .aiSummary(request.getSummary() != null ? request.getSummary() : "")
            .status(RoutineStatus.IN_PROGRESS)
            .build();
        
        routine = routineRepository.save(routine);
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional
    public RoutineResponse updateRoutineStatus(Long routineId, String status) {
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        routine.setStatus(RoutineStatus.valueOf(status.toUpperCase()));
        routineRepository.save(routine);
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional
    public ExerciseResponse toggleExerciseCompleted(Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exercise.setCompleted(!exercise.isCompleted());
        exerciseRepository.save(exercise);
        
        // 오늘 날짜의 루틴이고 모든 운동 완료 시에만 회고 시작
        if (exercise.isCompleted()) {
            // 오늘 날짜인지 확인
            boolean isTodayRoutine = routine.getDate().equals(LocalDate.now());
            
            if (isTodayRoutine) {
                // exercises가 이미 로드되어 있으므로 다시 확인
                boolean allCompleted = routine.getExercises().stream()
                    .allMatch(Exercise::isCompleted);
                
                log.info("운동 완료 토글: routineId={}, exerciseId={}, date={}, isToday={}, allCompleted={}, totalExercises={}, completedCount={}", 
                    routineId, exerciseId, routine.getDate(), isTodayRoutine, allCompleted, routine.getExercises().size(),
                    routine.getExercises().stream().filter(Exercise::isCompleted).count());
                
                if (allCompleted) {
                    log.info("오늘의 모든 운동 완료 - 회고 시작: memberId={}", routine.getMember().getId());
                    workoutReviewService.startWorkoutReview(routine.getMember().getId());
                }
            } else {
                log.debug("오늘 날짜가 아닌 루틴이므로 회고를 시작하지 않습니다: routineId={}, date={}", 
                    routineId, routine.getDate());
            }
        }
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public ExerciseResponse addExercise(Long routineId, ExerciseAddRequest request) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        // 다음 orderIndex 계산
        int nextOrderIndex = routine.getExercises().stream()
            .mapToInt(Exercise::getOrderIndex)
            .max()
            .orElse(-1) + 1;
        
        // ExerciseType 조회 또는 생성
        ExerciseType exerciseType = exerciseTypeRepository.findByName(request.getName())
            .orElseGet(() -> {
                // ExerciseType이 없으면 기본값으로 생성
                ExerciseCategory category = request.getCategory() != null
                    ? ExerciseCategory.valueOf(request.getCategory().toUpperCase())
                    : ExerciseCategory.CHEST; // 기본값
                ExerciseType newType = ExerciseType.builder()
                    .name(request.getName())
                    .mainTarget(category)
                    .subTargets(List.of())
                    .build();
                return exerciseTypeRepository.save(newType);
            });
        
        Exercise exercise = Exercise.builder()
            .name(request.getName())
            .exerciseType(exerciseType)
            .sets(request.getSets())
            .reps(request.getReps())
            .weight(request.getWeight())
            .orderIndex(nextOrderIndex)
            .completed(false)
            .routine(routine)
            .build();
        
        exerciseRepository.save(exercise);
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public ExerciseResponse updateExercise(Long routineId, Long exerciseId, ExerciseUpdateRequest request) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exercise.setName(request.getName());
        
        // ExerciseType 업데이트 (이름이 변경된 경우)
        if (exercise.getExerciseType() == null || !exercise.getExerciseType().getName().equals(request.getName())) {
            ExerciseType exerciseType = exerciseTypeRepository.findByName(request.getName())
                .orElseGet(() -> {
                    ExerciseCategory category = request.getCategory() != null 
                        ? ExerciseCategory.valueOf(request.getCategory().toUpperCase())
                        : (exercise.getExerciseType() != null 
                            ? exercise.getExerciseType().getMainTarget() 
                            : ExerciseCategory.CHEST);
                    ExerciseType newType = ExerciseType.builder()
                        .name(request.getName())
                        .mainTarget(category)
                        .subTargets(List.of())
                        .build();
                    return exerciseTypeRepository.save(newType);
                });
            exercise.setExerciseType(exerciseType);
        } else if (request.getCategory() != null) {
            // ExerciseType의 mainTarget 업데이트
            ExerciseCategory category = ExerciseCategory.valueOf(request.getCategory().toUpperCase());
            exercise.getExerciseType().setMainTarget(category);
        }
        if (request.getSets() != null) {
            exercise.setSets(request.getSets());
        }
        if (request.getReps() != null) {
            exercise.setReps(request.getReps());
        }
        if (request.getWeight() != null) {
            exercise.setWeight(request.getWeight());
        }
        
        exerciseRepository.save(exercise);
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public void deleteExercise(Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exerciseRepository.delete(exercise);
    }
    
    private RoutineResponse toRoutineResponse(Routine routine, boolean isToday) {
        List<ExerciseResponse> exerciseResponses = routine.getExercises().stream()
            .sorted(Comparator.comparing(Exercise::getOrderIndex))
            .map(this::toExerciseResponse)
            .collect(Collectors.toList());
        
        return RoutineResponse.builder()
            .id(routine.getId())
            .date(routine.getDate())
            .title(routine.getTitle())
            .status(routine.getStatus().name())
            .isToday(isToday)
            .summary(routine.getAiSummary())
            .exercises(exerciseResponses)
            .build();
    }
    
    private ExerciseResponse toExerciseResponse(Exercise exercise) {
        ExerciseType exerciseType = exercise.getExerciseType();
        // 이름과 타겟 정보는 ExerciseType을 기준으로 사용하고,
        // Exercise.name은 필요 시 커스텀 표시용으로만 사용 (null/없을 때만 fallback)
        String name = exerciseType != null && exerciseType.getName() != null
            ? exerciseType.getName()
            : exercise.getName();

        ExerciseCategory mainTarget = exerciseType != null 
            ? exerciseType.getMainTarget() 
            : ExerciseCategory.CHEST; // 기본값 (exerciseType이 없는 경우)
        List<String> subTargets = exerciseType != null && exerciseType.getSubTargets() != null
            ? exerciseType.getSubTargets().stream()
                .map(ExerciseCategory::name)
                .collect(Collectors.toList())
            : List.of();
        
        return ExerciseResponse.builder()
            .id(exercise.getId())
            .name(name)
            .mainTarget(mainTarget.name())
            .subTargets(subTargets)
            .sets(exercise.getSets())
            .reps(exercise.getReps())
            .weight(exercise.getWeight())
            .orderIndex(exercise.getOrderIndex())
            .completed(exercise.isCompleted())
            .build();
    }
}
