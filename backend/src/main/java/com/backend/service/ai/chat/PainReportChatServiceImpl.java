package com.backend.service.ai.chat;

import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.pain.WorkoutReviewService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PAIN_REPORT 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PainReportChatServiceImpl implements PainReportChatService {

    private final WorkoutReviewService workoutReviewService;
    private final CurrentMemberService currentMemberService;

    @Override
    public AIChatResponse handlePainReport(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        String bodyPart = (String) entities.get("body_part");
        int intensity = AIChatUtils.extractIntensity(entities);
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
}

