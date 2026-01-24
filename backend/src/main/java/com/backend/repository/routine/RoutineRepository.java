package com.backend.repository.routine;

import com.backend.domain.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    Optional<Routine> findByDateAndMemberId(LocalDate date, Long memberId);
    
    List<Routine> findByMemberIdAndDateBetween(Long memberId, LocalDate start, LocalDate end);
}

