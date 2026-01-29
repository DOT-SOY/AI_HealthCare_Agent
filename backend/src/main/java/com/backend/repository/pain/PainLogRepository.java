package com.backend.repository.pain;

import com.backend.domain.pain.PainLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PainLogRepository extends JpaRepository<PainLog, Long> {
    long countByMemberIdAndAreaAndCreatedAtAfter(Long memberId, String area, LocalDateTime after);
    
    List<PainLog> findByMemberIdAndAreaAndCreatedAtAfter(Long memberId, String area, LocalDateTime after);
}

