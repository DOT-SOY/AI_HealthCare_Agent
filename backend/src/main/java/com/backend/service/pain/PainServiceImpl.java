package com.backend.service.pain;

import com.backend.client.PainAdviceClient;
import com.backend.domain.pain.PainLog;
import com.backend.dto.response.PainAdviceResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.pain.PainLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PainServiceImpl implements PainService {
    
    private final PainLogRepository painLogRepository;
    private final MemberRepository memberRepository;
    private final PainAdviceClient painAdviceClient;
    
    @Override
    @Transactional
    public long reportPain(Long memberId, String area, int intensity, String description, boolean isRelatedToExercise) {
        log.info("통증 보고: memberId={}, area={}, intensity={}, isRelatedToExercise={}", 
            memberId, area, intensity, isRelatedToExercise);
        
        var member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + memberId));
        
        PainLog painLog = PainLog.builder()
            .member(member)
            .area(area)
            .description(description)
            .intensity(intensity)
            .createdAt(LocalDateTime.now())
            .isRelatedToExercise(isRelatedToExercise)
            .build();
        
        painLogRepository.save(painLog);
        
        // 에스컬레이션 카운트 계산 및 반환
        return calculateEscalationCount(memberId, area);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long calculateEscalationCount(Long memberId, String area) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return painLogRepository.countByMemberIdAndAreaAndCreatedAtAfter(memberId, area, sevenDaysAgo);
    }
    
    @Override
    public PainAdviceResponse getPainAdvice(String area, long count, String description) {
        log.debug("통증 조언 요청: area={}, count={}", area, count);
        return painAdviceClient.requestAdvice(area, count, description);
    }
}
