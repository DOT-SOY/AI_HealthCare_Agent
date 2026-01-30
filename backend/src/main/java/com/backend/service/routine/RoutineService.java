package com.backend.service.routine;

import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface RoutineService {
    RoutineResponse getTodayRoutine(Long memberId);

    /**
     * 특정 날짜의 루틴을 조회합니다.
     * AI 운동 회고(Workout Review)에서 사용됩니다.
     */
    RoutineResponse getRoutineByDate(Long memberId, java.time.LocalDate date);
    
    /**
     * 특정 날짜의 루틴을 필터링하여 조회합니다.
     * - exerciseName: 특정 운동 이름으로 필터링 (null이면 필터링 안 함)
     * - completed: 완료 여부로 필터링 (null이면 필터링 안 함, true면 완료된 운동만, false면 미완료 운동만)
     */
    RoutineResponse getRoutineByDateWithFilters(Long memberId, java.time.LocalDate date, String exerciseName, Boolean completed);
    
    List<RoutineResponse> getWeeklyRoutines(Long memberId);
    
    List<RoutineResponse> getHistory(Long memberId, String bodyPart);
    
    /**
     * 각 운동별로 가장 최신 루틴 1개씩 조회
     */
    Map<String, RoutineResponse> getLatestRoutinesByExercise(Long memberId);
    
    /**
     * 특정 운동의 루틴 목록을 페이지네이션으로 조회
     */
    Page<RoutineResponse> getRoutinesByExercise(Long memberId, String exerciseName, Pageable pageable);
    
    RoutineResponse getRoutineById(Long routineId);
    
    RoutineResponse createRoutine(Long memberId, RoutineCreateRequest request);
    
    RoutineResponse updateRoutineStatus(Long routineId, String status);
    
    ExerciseResponse toggleExerciseCompleted(Long routineId, Long exerciseId);
    
    ExerciseResponse addExercise(Long routineId, ExerciseAddRequest request);
    
    ExerciseResponse updateExercise(Long routineId, Long exerciseId, ExerciseUpdateRequest request);
    
    void deleteExercise(Long routineId, Long exerciseId);
}

