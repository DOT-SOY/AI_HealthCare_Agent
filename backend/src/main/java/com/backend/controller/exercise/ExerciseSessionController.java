package com.backend.controller.exercise;

import com.backend.dto.request.WorkoutFeedbackRequest;
import com.backend.dto.response.WorkoutFeedbackResponse;
import com.backend.client.WorkoutFeedbackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
@Slf4j
public class ExerciseSessionController {
    
    private final WorkoutFeedbackClient workoutFeedbackClient;
    
    /**
     * 운동 세션 완료 후 피드백을 요청합니다.
     * Python AI 서버를 통해 GPT 기반 피드백을 생성합니다.
     * 
     * @param request 운동 세션 데이터
     * @return 피드백 응답
     */
    @PostMapping("/session/feedback")
    public ResponseEntity<WorkoutFeedbackResponse> getSessionFeedback(
            @RequestBody WorkoutFeedbackRequest request) {
        log.info("운동 세션 피드백 요청: exercise_type={}, total_reps={}, duration_sec={}", 
                request.getExercise_type(), request.getTotal_reps(), request.getDuration_sec());
        
        WorkoutFeedbackResponse response = workoutFeedbackClient.requestFeedback(request);
        
        return ResponseEntity.ok(response);
    }
}


