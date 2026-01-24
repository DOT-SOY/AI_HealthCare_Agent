package com.backend.service.routine;

import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;

import java.util.List;

public interface RoutineService {
    RoutineResponse getTodayRoutine(Long memberId);
    
    List<RoutineResponse> getWeeklyRoutines(Long memberId);
    
    List<RoutineResponse> getHistory(Long memberId, String bodyPart);
    
    RoutineResponse getRoutineById(Long routineId);
    
    RoutineResponse updateRoutineStatus(Long routineId, String status);
    
    ExerciseResponse toggleExerciseCompleted(Long routineId, Long exerciseId);
    
    ExerciseResponse addExercise(Long routineId, ExerciseAddRequest request);
    
    ExerciseResponse updateExercise(Long routineId, Long exerciseId, ExerciseUpdateRequest request);
    
    void deleteExercise(Long routineId, Long exerciseId);
}

