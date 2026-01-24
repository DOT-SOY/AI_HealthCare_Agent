package com.backend.serviceImpl.pain;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.routine.Routine;
import com.backend.dto.response.PainAdviceResponse;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.PainService;
import com.backend.service.pain.WorkoutReviewService;
import com.backend.util.BodyPartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        
        // 모든 운동 완료 확인
        boolean allCompleted = todayRoutine.getExercises().stream()
            .allMatch(Exercise::isCompleted);
        
        if (!allCompleted) {
            log.debug("모든 운동이 완료되지 않았습니다: memberId={}", memberId);
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
        
        // 3. 오늘 루틴과 관련된 통증인지에 따라 응답 메시지 구성
        StringBuilder response = new StringBuilder();
        
        if (actualIsRelated) {
            // 오늘 루틴의 운동과 관련된 통증
            response.append("오늘 수행한 운동과 관련된 통증이 감지되었습니다.\n\n");
        } else {
            // 오늘 루틴과 관련 없는 통증
            response.append("오늘 수행한 운동과는 직접적인 관련이 없는 통증으로 보입니다.\n");
            response.append("자세 교정이나 일상생활 습관 개선이 도움이 될 수 있습니다.\n\n");
        }
        
        // 4. RAG 기반 조언 추가
        response.append(advice.getAdvice());
        
        // 5. 주에 3회 이상 같은 부위 통증 시 추가 경고 (운동과 연관 없는 통증만 카운트)
        if (escalationCount >= 3) {
            response.append("\n\n⚠️ 경고: 최근 7일 내 같은 부위 통증이 ");
            response.append(escalationCount);
            response.append("회 발생했습니다. ");
            response.append("지속적인 통증이 있다면 정형외과나 신경외과 전문의의 진료를 받으시기 바랍니다.");
        }
        
        return response.toString();
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
        
        // 오늘 루틴의 운동 카테고리 목록
        List<ExerciseCategory> todayCategories = routine.getExercises().stream()
            .map(Exercise::getCategory)
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
