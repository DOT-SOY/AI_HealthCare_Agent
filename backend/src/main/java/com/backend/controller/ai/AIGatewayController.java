package com.backend.controller.ai;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.ai.AIIntentService;
import com.backend.service.pain.WorkoutReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Gateway Controller
 * 
 * 하이브리드 구조:
 * 1. GENERAL_CHAT: 의도 분류와 답변을 한 번에 받아서 그대로 반환
 * 2. 기타 기능 (PAIN_REPORT, 음식 분석, 영상 분석 등): 
 *    - 의도 분류만 받고
 *    - 백엔드에서 각 Service를 통해 Python AI 서버의 특정 함수를 다시 호출
 *    - 각 기능은 독립적인 함수로 구현되어 다른 실행 루트에서도 재사용 가능
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIGatewayController {
    
    private final AIIntentService aiIntentService;
    private final WorkoutReviewService workoutReviewService;
    private final CurrentMemberService currentMemberService;
    
    /**
     * 텍스트 기반 AI 채팅 처리
     * 
     * 처리 흐름:
     * 1. 의도 분류 (Python AI 서버 /chat 호출)
     * 2. 의도에 따라 분기:
     *    - GENERAL_CHAT: Python AI 서버에서 이미 생성한 답변 그대로 반환
     *    - 기타 기능: 백엔드 Service를 통해 Python AI 서버의 특정 함수 재호출
     */
    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> handleAIChat(@RequestBody AIChatRequest request) {
        log.info("AI 채팅 요청: text={}", request.getText());
        
        // 1. 의도 분류 (Python AI 서버 호출)
        IntentClassificationResult classification = aiIntentService.classifyIntent(request.getText());
        
        // 2. 의도에 따라 적절한 Service 호출
        AIChatResponse response = switch (classification.getIntent()) {
            case "PAIN_REPORT" -> handlePainReport(classification);
            case "GENERAL_CHAT" -> handleGeneralChat(classification);
            case "WORKOUT_REVIEW" -> handleGeneralChat(classification); // 호환성을 위해 유지 (향후 Python AI 서버에서 제거 예정)
            // TODO: 향후 추가 예정
            // case "FOOD_ANALYSIS" -> handleFoodAnalysis(classification);
            // case "ROUTINE_MODIFY" -> handleRoutineModify(classification);
            // case "EXERCISE_ANALYSIS" -> handleExerciseAnalysis(classification);
            default -> createErrorResponse("알 수 없는 의도입니다.");
        };
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * PAIN_REPORT 의도 처리
     * 
     * 처리 방식: 의도 분류만 받고, 백엔드에서 Python AI 서버의 특정 함수를 다시 호출
     * - 의도 분류에서 entities 추출 (body_part, intensity)
     * - 백엔드 Service를 통해 통증 DB 저장 및 RAG 기반 조언 요청
     * - Python AI 서버의 /pain/advice 엔드포인트 호출
     * - 오늘 루틴과의 관련성 확인 및 에스컬레이션 처리
     * 
     * 참고: processPainReport는 다른 실행 루트에서도 재사용 가능
     */
    private AIChatResponse handlePainReport(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        String bodyPart = (String) entities.get("body_part");
        int intensity = extractIntensity(entities);
        String description = classification.getAiAnswer();
        
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        
        // 오늘 루틴과 관련된 통증인지 확인은 WorkoutReviewService에서 처리
        // processPainReport 내부에서 오늘 루틴을 조회하여 관련성 판단
        boolean isRelatedToExercise = false; // 파라미터는 유지하지만 내부에서 재계산됨
        
        String message = workoutReviewService.processPainReport(
            memberId, 
            bodyPart, 
            description, 
            intensity, 
            isRelatedToExercise
        );
        
        return AIChatResponse.builder()
            .message(message)
            .intent("PAIN_REPORT")
            .data(entities)
            .build();
    }
    
    /**
     * GENERAL_CHAT 의도 처리
     * 
     * 처리 방식: 의도 분류와 답변을 한 번에 받아서 그대로 반환
     * - Python AI 서버의 /chat 엔드포인트에서 의도 분류와 함께 답변도 생성
     * - classification.getAiAnswer()에 이미 생성된 답변이 포함됨
     * - DB 저장 없이 AI 응답만 반환
     */
    private AIChatResponse handleGeneralChat(IntentClassificationResult classification) {
        String aiAnswer = classification.getAiAnswer();
        
        // aiAnswer가 null이거나 빈 문자열인 경우 처리
        if (aiAnswer == null || aiAnswer.trim().isEmpty()) {
            log.warn("GENERAL_CHAT: Python AI 서버에서 aiAnswer가 비어있습니다. intent={}", classification.getIntent());
            aiAnswer = "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.";
        }
        
        log.info("GENERAL_CHAT 응답: intent={}, answerLength={}", classification.getIntent(), aiAnswer.length());
        
        return AIChatResponse.builder()
            .message(aiAnswer)
            .intent("GENERAL_CHAT")
            .build();
    }
    
    // TODO: 향후 추가 예정 - 각 기능별 처리 메서드
    // /**
    //  * FOOD_ANALYSIS 의도 처리
    //  * - 의도 분류만 받고, 백엔드에서 Python AI 서버의 /food/analyze 함수 호출
    //  * - FoodAnalysisService를 통해 처리 (다른 실행 루트에서도 재사용 가능)
    //  */
    // private AIChatResponse handleFoodAnalysis(IntentClassificationResult classification) {
    //     // FoodAnalysisService 호출
    // }
    //
    // /**
    //  * EXERCISE_ANALYSIS 의도 처리
    //  * - 의도 분류만 받고, 백엔드에서 Python AI 서버의 /exercise/analyze 함수 호출
    //  * - ExerciseAnalysisService를 통해 처리 (다른 실행 루트에서도 재사용 가능)
    //  */
    // private AIChatResponse handleExerciseAnalysis(IntentClassificationResult classification) {
    //     // ExerciseAnalysisService 호출
    // }
    
    private AIChatResponse createErrorResponse(String errorMessage) {
        return AIChatResponse.builder()
            .message(errorMessage)
            .intent("ERROR")
            .build();
    }
    
    private int extractIntensity(java.util.Map<String, Object> entities) {
        Object intensityObj = entities.get("intensity");
        if (intensityObj instanceof Number) {
            return ((Number) intensityObj).intValue();
        }
        if (intensityObj instanceof String) {
            try {
                return Integer.parseInt((String) intensityObj);
            } catch (NumberFormatException e) {
                log.warn("intensity 파싱 실패: {}", intensityObj);
            }
        }
        return 5; // 기본값
    }
}
