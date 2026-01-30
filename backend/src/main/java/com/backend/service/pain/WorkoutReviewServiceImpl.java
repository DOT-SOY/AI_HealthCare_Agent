package com.backend.service.pain;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.routine.Routine;
import com.backend.dto.response.PainAdviceResponse;
import com.backend.repository.routine.RoutineRepository;
import com.backend.util.BodyPartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutReviewServiceImpl implements WorkoutReviewService {
    
    private final RoutineRepository routineRepository;
    private final PainService painService;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Override
    public void startWorkoutReview(Long memberId) {
        log.info("운동 회고 시작: memberId={}", memberId);
        
        Routine todayRoutine = routineRepository.findByDateAndMemberId(LocalDate.now(), memberId)
            .orElse(null);
        
        if (todayRoutine == null) {
            log.warn("오늘 루틴을 찾을 수 없습니다: memberId={}", memberId);
            return;
        }
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        int totalExercises = todayRoutine.getExercises().size();
        long completedCount = todayRoutine.getExercises().stream()
            .filter(Exercise::isCompleted)
            .count();
        
        log.info("운동 완료 상태 확인: memberId={}, routineId={}, totalExercises={}, completedCount={}", 
            memberId, todayRoutine.getId(), totalExercises, completedCount);
        
        // 모든 운동 완료 확인
        boolean allCompleted = totalExercises > 0 && completedCount == totalExercises;
        
        if (!allCompleted) {
            log.debug("모든 운동이 완료되지 않았습니다: memberId={}, total={}, completed={}", 
                memberId, totalExercises, completedCount);
            return;
        }
        
        // WebSocket을 통해 알림 전송
        messagingTemplate.convertAndSend(
            "/topic/workout/review",
            new ReviewNotificationMessage(
                todayRoutine.getId(),
                "오늘 운동은 어땠나요? 피드백을 주시면 다음 루틴에 반영하겠습니다."
            )
        );
        
        log.info("운동 회고 알림 전송 완료: memberId={}, routineId={}", memberId, todayRoutine.getId());
    }
    
    /**
     * 통증 처리
     * 
     * 이 메서드는 독립적인 함수로 구현되어 있어:
     * - AIGatewayController에서 호출 가능
     * - 다른 실행 루트(예: 직접 통증 보고 API)에서도 재사용 가능
     * 
     * 처리 흐름:
     * 1. 통증을 DB에 저장 (운동과 연관된 통증은 저장하지 않음)
     * 2. Python AI 서버의 /pain/advice 엔드포인트 호출 (RAG 기반 조언)
     * 3. DB 저장 횟수에 따라 메시지 구성 (에스컬레이션 처리, 운동과 연관 없는 통증만 카운트)
     */
    @Override
    public String processPainReport(
        Long memberId, 
        String bodyPart, 
        String description, 
        int intensity, 
        boolean isRelatedToExercise
    ) {
        log.info("통증 처리: memberId={}, bodyPart={}, intensity={}, isRelatedToExercise={}", 
            memberId, bodyPart, intensity, isRelatedToExercise);
        
        // 오늘 루틴 조회하여 실제 관련성 확인
        Routine todayRoutine = routineRepository.findByDateAndMemberId(LocalDate.now(), memberId)
            .orElse(null);
        
        boolean actualIsRelated = false;
        if (todayRoutine != null) {
            actualIsRelated = isPainRelatedToTodayRoutine(todayRoutine, bodyPart);
        }
        
        // 1. 통증을 DB에 저장 (운동과 연관된 통증은 저장하지 않음)
        long escalationCount = 0L;
        if (!actualIsRelated) {
            // 운동과 연관 없는 통증만 DB에 저장
            escalationCount = painService.reportPain(
                memberId, 
                bodyPart, 
                intensity, 
                description, 
                false
            );
        } else {
            // 운동과 연관된 통증은 일시적인 것으로 간주하여 DB 저장하지 않음
            log.info("운동과 연관된 통증이므로 DB 저장을 건너뜁니다: memberId={}, bodyPart={}", memberId, bodyPart);
        }
        
        // 2. Python AI 서버에 통증 조언 요청 (RAG 기반)
        // 운동과 연관된 통증도 조언은 제공 (escalationCount는 0으로 전달)
        PainAdviceResponse advice = painService.getPainAdvice(bodyPart, escalationCount, description);
        
        // 3. 통증 부위를 사용자 친화적인 표현으로 변환
        String bodyPartKr = formatBodyPartForMessage(bodyPart);
        
        // 4. intensity에 따른 통증 강도 표현
        String intensityDescription = formatIntensityForMessage(intensity);
        
        // 5. 오늘 루틴과 관련된 통증인지에 따라 응답 메시지 구성
        StringBuilder response = new StringBuilder();
        
        if (actualIsRelated) {
            // 오늘 루틴의 운동과 관련된 통증 - 친근하고 공감적인 톤
            response.append("오늘 운동 후 ").append(bodyPartKr).append("에 ");
            if (intensity >= 7) {
                response.append("통증이 ").append(intensityDescription).append(" 느껴지시는군요. ");
                response.append("운동 강도가 높아서 그럴 수 있습니다. 😟\n");
            } else {
                response.append("통증이 느껴지시는군요. 😊\n");
            }
            response.append("운동으로 인한 일시적인 통증일 가능성이 높습니다.\n\n");
            response.append("다음과 같은 방법을 시도해보세요:\n");
            response.append(replaceBodyPartTerms(advice.getAdvice()));
            if (intensity >= 7) {
                response.append("\n\n통증이 심하시다면 운동을 잠시 쉬시고, ");
                response.append("통증이 완전히 사라질 때까지 휴식을 취하시는 것을 권장드립니다.");
            } else {
                response.append("\n\n통증이 계속되거나 심해지면 운동 강도를 조절하거나 휴식을 취하는 것도 좋은 방법입니다.");
            }
        } else {
            // 오늘 루틴과 관련 없는 통증 - 걱정을 이해하고 조언 제공
            response.append(bodyPartKr).append(" 통증이 ");
            if (intensity >= 7) {
                response.append(intensityDescription).append(" 걱정되시는군요. ");
            } else {
                response.append("걱정되시는군요. ");
            }
            response.append("오늘 운동과는 직접적인 관련이 없어 보이지만, ");
            response.append("일상생활에서의 자세나 습관이 원인일 수 있습니다.\n\n");
            response.append("다음과 같은 방법을 시도해보세요:\n");
            response.append(replaceBodyPartTerms(advice.getAdvice()));
            if (intensity >= 7) {
                response.append("\n\n통증이 심하시다면 가능한 한 빨리 전문의 상담을 받아보시는 것을 강력히 권장드립니다.");
            } else {
                response.append("\n\n통증이 지속되면 전문의 상담을 받아보시는 것을 권장드립니다.");
            }
        }
        
        // 5. 주에 3회 이상 같은 부위 통증 시 추가 경고 (운동과 연관 없는 통증만 카운트)
        if (escalationCount >= 3) {
            response.append("\n\n⚠️ ").append(bodyPartKr).append(" 통증이 최근 7일 동안 ");
            response.append(escalationCount).append("회 발생했네요.\n\n");
            response.append("이런 빈도는 일상적인 통증보다는 주의가 필요합니다. ");
            response.append("정형외과나 신경외과 전문의의 진료를 받아보시는 것을 강력히 권장드립니다.\n\n");
            response.append("건강이 최우선이니, 통증이 계속되면 운동을 잠시 중단하고 ");
            response.append("전문의의 조언을 구하시기 바랍니다. 🙏");
        }
        
        return response.toString();
    }
    
    /**
     * 통증 강도를 사용자 친화적인 표현으로 변환합니다.
     */
    private String formatIntensityForMessage(int intensity) {
        if (intensity >= 8) {
            return "심하게";
        } else if (intensity >= 6) {
            return "꽤";
        } else if (intensity >= 4) {
            return "조금";
        } else {
            return "살짝";
        }
    }
    
    /**
     * 통증 부위를 사용자 친화적인 한국어 표현으로 변환합니다.
     * ENUM 형식(BACK, CHEST 등)이면 한국어로, 이미 한글이면 그대로 반환.
     */
    private String formatBodyPartForMessage(String bodyPart) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return "해당 부위";
        }
        
        // ENUM 형식인 경우 한국어로 변환
        String upper = bodyPart.toUpperCase();
        return switch (upper) {
            case "BACK" -> "등";
            case "CHEST" -> "가슴";
            case "SHOULDER" -> "어깨";
            case "ARM" -> "팔";
            case "CORE" -> "코어";
            case "ABS" -> "복근";
            case "GLUTE" -> "둔근";
            case "THIGH" -> "허벅지";
            case "CALF" -> "종아리";
            default -> bodyPart; // 이미 한글이거나 다른 형식이면 그대로 반환
        };
    }
    
    /**
     * AI 응답 텍스트에서 영어 부위 용어를 한글로 치환합니다.
     * 예: "글루트", "GLUTE" -> "둔근"
     */
    private String replaceBodyPartTerms(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        String result = text;
        
        // 영어 ENUM 형식 치환 (대소문자 구분 없이)
        result = result.replaceAll("(?i)\\bGLUTE\\b", "둔근");
        result = result.replaceAll("(?i)\\bBACK\\b", "등");
        result = result.replaceAll("(?i)\\bCHEST\\b", "가슴");
        result = result.replaceAll("(?i)\\bSHOULDER\\b", "어깨");
        result = result.replaceAll("(?i)\\bARM\\b", "팔");
        result = result.replaceAll("(?i)\\bCORE\\b", "코어");
        result = result.replaceAll("(?i)\\bABS\\b", "복근");
        result = result.replaceAll("(?i)\\bTHIGH\\b", "허벅지");
        result = result.replaceAll("(?i)\\bCALF\\b", "종아리");
        
        // 한글 음성 표기 치환
//        result = result.replace("글루트", "둔근");
//        result = result.replace("글루티", "둔근");
        
        return result;
    }
    
    /**
     * 오늘 루틴의 운동과 통증 부위가 관련이 있는지 확인합니다.
     */
    private boolean isPainRelatedToTodayRoutine(Routine routine, String bodyPart) {
        // 통증 부위를 ExerciseCategory로 변환
        ExerciseCategory painCategory = BodyPartMapper.mapBodyPartToCategory(bodyPart);
        
        if (painCategory == null) {
            return false;
        }
        
        // 오늘 루틴의 운동 카테고리 목록 (exerciseType의 mainTarget 사용)
        List<ExerciseCategory> todayCategories = routine.getExercises().stream()
            .map(ex -> ex.getExerciseType() != null 
                ? ex.getExerciseType().getMainTarget() 
                : ExerciseCategory.CHEST) // 기본값
            .distinct()
            .collect(Collectors.toList());
        
        // 오늘 루틴에 해당 카테고리의 운동이 있는지 확인
        return todayCategories.contains(painCategory);
    }
    
    /**
     * WebSocket 메시지 클래스
     */
    public static class ReviewNotificationMessage {
        private Long routineId;
        private String message;
        
        public ReviewNotificationMessage(Long routineId, String message) {
            this.routineId = routineId;
            this.message = message;
        }
        
        public Long getRoutineId() {
            return routineId;
        }
        
        public void setRoutineId(Long routineId) {
            this.routineId = routineId;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
