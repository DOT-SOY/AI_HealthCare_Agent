package com.backend.serviceImpl.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.repository.exercise.ExerciseRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.WorkoutReviewService;
import com.backend.service.routine.RoutineService;
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
    private final WorkoutReviewService workoutReviewService;
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getTodayRoutine(Long memberId) {
        Routine routine = routineRepository.findByDateAndMemberId(LocalDate.now(), memberId)
            .orElse(null);
        
        if (routine == null) {
            return null;
        }
        
        return toRoutineResponse(routine, true);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getWeeklyRoutines(Long memberId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(memberId, weekStart, today);
        
        return routines.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(today)))
            .sorted(Comparator.comparing(RoutineResponse::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getHistory(Long memberId, String bodyPart) {
        // 특정 부위의 통증과 관련된 루틴 조회 로직
        // 현재는 전체 루틴 반환 (추후 확장 가능)
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(
            memberId, 
            LocalDate.now().minusMonths(3), 
            LocalDate.now()
        );
        
        return routines.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(LocalDate.now())))
            .sorted(Comparator.comparing(RoutineResponse::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutineById(Long routineId) {
        Routine routine = routineRepository.findById(routineId)
            .orElse(null);
        
        if (routine == null) {
            return null;
        }
        
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
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exercise.setCompleted(!exercise.isCompleted());
        exerciseRepository.save(exercise);
        
        // 모든 운동 완료 시 회고 시작
        if (exercise.isCompleted()) {
            boolean allCompleted = routine.getExercises().stream()
                .allMatch(Exercise::isCompleted);
            
            if (allCompleted) {
                workoutReviewService.startWorkoutReview(routine.getMember().getId());
            }
        }
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public ExerciseResponse addExercise(Long routineId, ExerciseAddRequest request) {
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // 다음 orderIndex 계산
        int nextOrderIndex = routine.getExercises().stream()
            .mapToInt(Exercise::getOrderIndex)
            .max()
            .orElse(-1) + 1;
        
        Exercise exercise = Exercise.builder()
            .name(request.getName())
            .category(ExerciseCategory.valueOf(request.getCategory().toUpperCase()))
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
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exercise.setName(request.getName());
        exercise.setCategory(ExerciseCategory.valueOf(request.getCategory().toUpperCase()));
        exercise.setSets(request.getSets());
        exercise.setReps(request.getReps());
        exercise.setWeight(request.getWeight());
        
        exerciseRepository.save(exercise);
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public void deleteExercise(Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
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
            .status(routine.getStatus().name())
            .isToday(isToday)
            .summary(routine.getAiSummary())
            .exercises(exerciseResponses)
            .build();
    }
    
    private ExerciseResponse toExerciseResponse(Exercise exercise) {
        return ExerciseResponse.builder()
            .id(exercise.getId())
            .name(exercise.getName())
            .category(exercise.getCategory().name())
            .sets(exercise.getSets())
            .reps(exercise.getReps())
            .weight(exercise.getWeight())
            .orderIndex(exercise.getOrderIndex())
            .completed(exercise.isCompleted())
            .build();
    }
}
