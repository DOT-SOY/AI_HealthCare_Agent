package com.backend.controller.ai;

import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.dto.response.RoutineResponse;
import com.backend.service.routine.RoutineService;
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
    private final RoutineService routineService;
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
        
        String intent = classification.getIntent();
        
        // 2. 의도에 따라 적절한 Service 호출
        AIChatResponse response = switch (intent) {
            case "PAIN_REPORT" -> handlePainReport(classification);
            case "GENERAL_CHAT" -> handleGeneralChat(classification);
            case "WORKOUT" -> handleWorkout(classification);
            // TODO: 향후 추가 예정
            // case "FOOD_ANALYSIS" -> handleFoodAnalysis(classification);
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

    /**
     * WORKOUT 의도 처리 (대분류: intent)
     *
     * action(소분류)에 따라 분기:
     * - QUERY: 루틴 조회 (운동 기록, 회고 등 포함)
     * - RECOMMEND: 운동 추천 (추후 구현)
     * - MODIFY: 루틴 수정 (추후 구현)
     */
    private AIChatResponse handleWorkout(IntentClassificationResult classification) {
        String action = classification.getAction();
        
        if (action == null) {
            log.warn("WORKOUT intent에서 action이 null입니다. 일반 채팅으로 처리");
            return handleGeneralChat(classification);
        }

        return switch (action.toUpperCase()) {
            case "QUERY" -> handleWorkoutQuery(classification);
            case "RECOMMEND" -> handleWorkoutRecommend(classification);
            case "MODIFY" -> handleWorkoutModify(classification);
            default -> {
                log.info("WORKOUT intent에서 지원하지 않는 action: {}, 일반 채팅으로 처리", action);
                yield handleGeneralChat(classification);
            }
        };
    }
    
    /**
     * WORKOUT의 QUERY 액션 처리 (소분류: action)
     * 
     * - entities.date를 기준으로 해당 날짜의 루틴을 조회
     * - RoutineResponse를 data에 담아서 프론트로 전달
     * - 루틴 데이터를 기반으로 자연스러운 메시지 생성
     */
    private AIChatResponse handleWorkoutQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;

        java.time.LocalDate targetDate = resolveDate(dateObj);

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse routine = routineService.getRoutineByDate(memberId, targetDate);

        String message;
        
        if (routine == null) {
            // 루틴이 없을 때: 풍부하고 친근한 메시지
            message = generateNoRoutineMessage(targetDate);
        } else {
            // 루틴이 있을 때: 루틴 데이터를 기반으로 직접 자연스러운 메시지 생성
            message = generateRoutineBasedMessage(routine, targetDate);
        }

        return AIChatResponse.builder()
            .message(message)
            .intent("WORKOUT")
            .data(routine)
            .build();
    }

    /**
     * WORKOUT의 RECOMMEND 액션 처리 (소분류: action)
     * 
     * - 사용자의 상태, 목표, 과거 루틴 등을 분석하여 운동 추천
     * - 추후 구현 예정
     */
    private AIChatResponse handleWorkoutRecommend(IntentClassificationResult classification) {
        // TODO: 추후 구현
        // - 사용자의 과거 루틴 분석
        // - 통증 이력 확인
        // - 목표 및 선호도 고려
        // - AI 기반 운동 추천 생성
        
        log.info("WORKOUT RECOMMEND 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("운동 추천 기능은 곧 제공될 예정입니다.")
            .intent("WORKOUT")
            .build();
    }

    /**
     * WORKOUT의 MODIFY 액션 처리 (소분류: action)
     * 
     * - 루틴 수정, 운동 추가/삭제, 세트/횟수/무게 변경 등
     * - 추후 구현 예정
     */
    private AIChatResponse handleWorkoutModify(IntentClassificationResult classification) {
        // TODO: 추후 구현
        // - entities에서 수정할 루틴 정보 추출 (date, exercise_name 등)
        // - RoutineService를 통해 루틴 수정
        // - 수정 결과를 자연어로 변환하여 응답
        
        log.info("WORKOUT MODIFY 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("루틴 수정 기능은 곧 제공될 예정입니다.")
            .intent("WORKOUT")
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

    /**
     * entities.date 값을 LocalDate로 변환합니다.
     * - "today" 또는 null: 오늘 날짜
     * - "YYYY-MM-DD" 형식 문자열: 해당 날짜
     * - 그 외: 오늘 날짜 (fallback)
     */
    private java.time.LocalDate resolveDate(Object dateObj) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (dateObj == null) {
            return today;
        }
        if (dateObj instanceof String dateStr) {
            String trimmed = dateStr.trim();
            if (trimmed.equalsIgnoreCase("today") || trimmed.isEmpty()) {
                return today;
            }
            try {
                return java.time.LocalDate.parse(trimmed);
            } catch (Exception e) {
                log.warn("날짜 파싱 실패, today로 대체: {}", trimmed);
                return today;
            }
        }
        return today;
    }

    /**
     * 날짜를 사용자 친화적인 메시지 형식으로 변환합니다.
     */
    private String formatDateForMessage(java.time.LocalDate date) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (date.equals(today)) {
            return "오늘";
        } else if (date.equals(today.minusDays(1))) {
            return "어제";
        } else if (date.equals(today.minusDays(2))) {
            return "그저께";
        } else {
            return date.toString();
        }
    }

    /**
     * 루틴이 없을 때 풍부하고 친근한 메시지를 생성합니다.
     */
    private String generateNoRoutineMessage(java.time.LocalDate targetDate) {
        String dateStr = formatDateForMessage(targetDate);
        java.time.LocalDate today = java.time.LocalDate.now();
        
        StringBuilder sb = new StringBuilder();
        sb.append(dateStr).append("에는 운동 기록이 없습니다. ");
        
        // 날짜에 따른 추가 메시지
        if (targetDate.equals(today)) {
            sb.append("오늘 운동 계획을 세우시거나 새로운 루틴을 시작해보시는 건 어떨까요? 💪");
        } else if (targetDate.isBefore(today)) {
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(targetDate, today);
            if (daysAgo == 1) {
                sb.append("어제는 쉬는 날이셨나요? 오늘은 운동하시는 걸 추천드려요!");
            } else if (daysAgo <= 7) {
                sb.append("그때는 운동을 하지 않으셨네요. 꾸준한 운동이 중요하니 오늘부터 다시 시작해보세요!");
            } else {
                sb.append("그때는 운동 기록이 없었네요. 지금부터 꾸준히 운동하시면 좋은 결과가 있을 거예요!");
            }
        } else {
            sb.append("미래 날짜네요! 그날 운동 계획을 미리 세워보시는 것도 좋은 방법입니다.");
        }
        
        return sb.toString();
    }

    /**
     * 루틴 데이터를 기반으로 자연스러운 메시지를 생성합니다.
     * AI의 되묻기 형식 답변 대신, 실제 루틴 데이터를 바탕으로 직접 답변합니다.
     */
    private String generateRoutineBasedMessage(RoutineResponse routine, java.time.LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        String dateStr = formatDateForMessage(targetDate);
        
        sb.append(dateStr).append(" 운동 기록을 확인했습니다. ");
        
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            // 1개든 여러 개든 상세하게 표시
            sb.append(formatRoutineMessage(routine, targetDate));
            
            int totalExercises = routine.getExercises().size();
            long completedCount = routine.getExercises().stream()
                .filter(ex -> ex.isCompleted())
                .count();
            
            // 완료 상태에 따른 추가 메시지
            if (completedCount == totalExercises) {
                sb.append("\n\n모든 운동을 완료하셨습니다! 👍");
            } else if (completedCount > 0) {
                sb.append("\n\n").append(completedCount).append("개 운동을 완료하셨고, ");
                sb.append(totalExercises - completedCount).append("개가 남아있습니다.");
            } else {
                sb.append("\n\n아직 시작하지 않은 루틴입니다.");
            }
        } else {
            sb.append("등록된 운동이 없습니다.");
        }
        
        return sb.toString();
    }

    /**
     * 루틴 데이터를 자연어 메시지로 포맷팅합니다.
     */
    private String formatRoutineMessage(RoutineResponse routine, java.time.LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        
        // 날짜와 제목
        String dateStr = formatDateForMessage(targetDate);
        sb.append(dateStr).append(" 루틴: ");
        if (routine.getTitle() != null && !routine.getTitle().trim().isEmpty()) {
            sb.append(routine.getTitle());
        } else {
            sb.append("운동 루틴");
        }
        sb.append("\n\n");

        // 운동 목록
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            sb.append("운동 목록:\n");
            for (int i = 0; i < routine.getExercises().size(); i++) {
                var exercise = routine.getExercises().get(i);
                sb.append(i + 1).append(". ");
                // ExerciseResponse의 name 필드 사용
                String exerciseName = exercise.getName() != null
                    ? exercise.getName()
                    : "알 수 없는 운동";
                sb.append(exerciseName);
                
                // 세트, 횟수, 무게 정보
                if (exercise.getSets() != null && exercise.getReps() != null) {
                    sb.append(" - ").append(exercise.getSets()).append("세트 × ");
                    sb.append(exercise.getReps()).append("회");
                    if (exercise.getWeight() != null && exercise.getWeight() > 0) {
                        sb.append(" (").append(exercise.getWeight()).append("kg)");
                    }
                }
                
                // 완료 여부
                if (exercise.isCompleted()) {
                    sb.append(" ✓ 완료");
                } else {
                    sb.append(" (미완료)");
                }
                
                sb.append("\n");
            }
        } else {
            sb.append("등록된 운동이 없습니다.");
        }

        // 상태 정보
        if (routine.getStatus() != null) {
            String statusKr = switch (routine.getStatus().toUpperCase()) {
                case "EXPECTED" -> "예정";
                case "IN_PROGRESS" -> "진행 중";
                case "COMPLETED" -> "완료";
                case "CANCELLED" -> "취소됨";
                default -> routine.getStatus();
            };
            sb.append("\n상태: ").append(statusKr);
        }

        return sb.toString();
    }
}
