package com.backend.repository.routine;

import com.backend.domain.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    @Query("SELECT r FROM Routine r LEFT JOIN FETCH r.exercises e LEFT JOIN FETCH e.exerciseType WHERE r.date = :date AND r.member.id = :memberId")
    Optional<Routine> findByDateAndMemberId(@Param("date") LocalDate date, @Param("memberId") Long memberId);
    
    @Query("SELECT DISTINCT r FROM Routine r LEFT JOIN FETCH r.exercises e LEFT JOIN FETCH e.exerciseType WHERE r.member.id = :memberId AND r.date BETWEEN :start AND :end ORDER BY r.date DESC")
    List<Routine> findByMemberIdAndDateBetween(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    
    @Query("SELECT r FROM Routine r LEFT JOIN FETCH r.exercises e LEFT JOIN FETCH e.exerciseType WHERE r.id = :id")
    Optional<Routine> findByIdWithExercises(@Param("id") Long id);
}

