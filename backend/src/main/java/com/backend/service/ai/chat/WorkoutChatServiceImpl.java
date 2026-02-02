package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.dto.response.RoutineResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.routine.RoutineService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * WORKOUT 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutChatServiceImpl implements WorkoutChatService {

    private final RoutineService routineService;
    private final CurrentMemberService currentMemberService;
    private final GeneralChatService generalChatService;

    @Override
    public AIChatResponse handleWorkout(IntentClassificationResult classification) {
        String action = classification.getAction();
        
        if (action == null) {
            log.warn("WORKOUT intent에서 action이 null입니다. 일반 채팅으로 처리");
            return generalChatService.handleGeneralChat(classification);
        }

        return switch (action.toUpperCase()) {
            case "QUERY" -> handleWorkoutQuery(classification);
            case "RECOMMEND" -> handleWorkoutRecommend(classification);
            case "MODIFY" -> handleWorkoutModify(classification);
            default -> {
                log.info("WORKOUT intent에서 지원하지 않는 action: {}, 일반 채팅으로 처리", action);
                yield generalChatService.handleGeneralChat(classification);
            }
        };
    }

    /**
     * WORKOUT의 QUERY 액션 처리 (소분류: action)
     * 
     * - entities.date를 기준으로 해당 날짜의 루틴을 조회
     * - exercise_name, exercise_completed 필터링 지원
     * - RoutineResponse를 data에 담아서 프론트로 전달
     * - 루틴 데이터를 기반으로 자연스러운 메시지 생성
     */
    private AIChatResponse handleWorkoutQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;
        Object exerciseNameObj = entities != null ? entities.get("exercise_name") : null;
        Object exerciseCompletedObj = entities != null ? entities.get("exercise_completed") : null;

        LocalDate targetDate = AIChatUtils.resolveDate(dateObj);
        String exerciseName = exerciseNameObj != null ? exerciseNameObj.toString() : null;
        Boolean completed = parseExerciseCompleted(exerciseCompletedObj);

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse routine;
        
        if (exerciseName != null || completed != null) {
            routine = routineService.getRoutineByDateWithFilters(memberId, targetDate, exerciseName, completed);
        } else {
            routine = routineService.getRoutineByDate(memberId, targetDate);
        }

        String message;
        
        if (routine == null) {
            // 루틴이 없을 때: 풍부하고 친근한 메시지
            message = generateNoRoutineMessage(targetDate, exerciseName, completed);
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
     * exercise_completed 엔티티를 Boolean으로 파싱합니다.
     */
    private Boolean parseExerciseCompleted(Object exerciseCompletedObj) {
        if (exerciseCompletedObj == null) {
            return null;
        }
        if (exerciseCompletedObj instanceof Boolean) {
            return (Boolean) exerciseCompletedObj;
        }
        if (exerciseCompletedObj instanceof String) {
            String str = ((String) exerciseCompletedObj).toLowerCase();
            if (str.equals("true") || str.equals("완료") || str.equals("완료됨") || str.equals("했어") || str.equals("했음")) {
                return true;
            }
            if (str.equals("false") || str.equals("미완료") || str.equals("안했어") || str.equals("안했음")) {
                return false;
            }
        }
        return null;
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

    /**
     * 루틴이 없을 때 풍부하고 친근한 메시지를 생성합니다.
     */
    private String generateNoRoutineMessage(LocalDate targetDate, String exerciseName, Boolean completed) {
        String dateStr = AIChatUtils.formatDateForMessage(targetDate);
        LocalDate today = LocalDate.now();
        
        StringBuilder sb = new StringBuilder();
        
        // 필터링 조건이 있는 경우
        if (exerciseName != null || completed != null) {
            sb.append(dateStr).append("에 ");
            if (exerciseName != null) {
                sb.append("'").append(exerciseName).append("' ");
            }
            if (completed != null) {
                sb.append(completed ? "완료된 " : "미완료인 ");
            }
            sb.append("운동 기록이 없습니다. ");
        } else {
            sb.append(dateStr).append("에는 운동 기록이 없습니다. ");
        }
        
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
    private String generateRoutineBasedMessage(RoutineResponse routine, LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        String dateStr = AIChatUtils.formatDateForMessage(targetDate);
        
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            int totalExercises = routine.getExercises().size();
            long completedCount = routine.getExercises().stream()
                .filter(ex -> ex.isCompleted())
                .count();
            
            // 완료 상태에 따른 인사말
            if (completedCount == totalExercises) {
                sb.append(dateStr).append(" 운동 기록을 확인했어요! 모든 운동을 완료하셨네요! 🎉\n\n");
            } else if (completedCount > 0) {
                sb.append(dateStr).append(" 운동 기록을 확인했어요! ").append(completedCount).append("개 운동을 완료하셨고, ");
                sb.append(totalExercises - completedCount).append("개가 남아있어요.\n\n");
            } else {
                sb.append(dateStr).append(" 운동 계획을 확인했어요! 아직 시작하지 않은 루틴이네요. 화이팅! 💪\n\n");
            }
            
            // 상세 정보
            sb.append(formatRoutineMessage(routine, targetDate));
        } else {
            sb.append(dateStr).append(" 운동 기록을 확인했는데, 등록된 운동이 없네요.");
        }
        
        return sb.toString();
    }

    /**
     * 루틴 데이터를 자연어 메시지로 포맷팅합니다.
     */
    private String formatRoutineMessage(RoutineResponse routine, LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        
        // 제목
        if (routine.getTitle() != null && !routine.getTitle().trim().isEmpty() && !routine.getTitle().equals("새로운 루틴")) {
            sb.append("📋 ").append(routine.getTitle()).append("\n\n");
        }

        // 운동 목록
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            for (int i = 0; i < routine.getExercises().size(); i++) {
                var exercise = routine.getExercises().get(i);
                String exerciseName = exercise.getName() != null
                    ? exercise.getName()
                    : "알 수 없는 운동";
                
                // 완료 여부에 따른 이모지
                if (exercise.isCompleted()) {
                    sb.append("✅ ");
                } else {
                    sb.append("⏳ ");
                }
                
                sb.append(exerciseName);
                
                // 세트, 횟수, 무게 정보
                if (exercise.getSets() != null && exercise.getReps() != null) {
                    sb.append(" - ").append(exercise.getSets()).append("세트 × ");
                    sb.append(exercise.getReps()).append("회");
                    if (exercise.getWeight() != null && exercise.getWeight() > 0) {
                        sb.append(" (").append(exercise.getWeight()).append("kg)");
                    }
                }
                
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}

