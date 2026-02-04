package com.backend.client;

import com.backend.dto.request.WorkoutFeedbackRequest;
import com.backend.dto.response.WorkoutFeedbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutFeedbackClient {
    
    private final BaseAIClient baseAIClient;
    
    /**
     * Python AI 서버의 /workout/feedback 엔드포인트를 호출하여 운동 세션 피드백을 받습니다.
     * 
     * @param request 운동 세션 데이터
     * @return 피드백 응답
     */
    public WorkoutFeedbackResponse requestFeedback(WorkoutFeedbackRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("exercise_type", request.getExercise_type());
        requestBody.put("total_reps", request.getTotal_reps());
        requestBody.put("duration_sec", request.getDuration_sec());
        requestBody.put("main_issue", request.getMain_issue());
        requestBody.put("bad_posture_ratio", request.getBad_posture_ratio());
        
        return baseAIClient.postRequest("/workout/feedback", requestBody, WorkoutFeedbackResponse.class);
    }
}


