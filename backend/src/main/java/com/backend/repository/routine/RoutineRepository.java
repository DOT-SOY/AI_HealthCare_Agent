package com.backend.repository.routine;

import com.backend.domain.routine.Routine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
           /**
            * 특정 운동 이름의 완료된 루틴을 페이지네이션으로 조회
            */
           @Query("""
               SELECT DISTINCT r 
               FROM Routine r 
               LEFT JOIN FETCH r.exercises e 
               LEFT JOIN FETCH e.exerciseType 
               WHERE r.member.id = :memberId 
               AND r.date BETWEEN :start AND :end 
               AND EXISTS (
                   SELECT 1 FROM Exercise ex 
                   JOIN ex.exerciseType et
                   WHERE ex.routine.id = r.id 
                   AND ex.completed = true 
                   AND et.name = :exerciseName
               )
               ORDER BY r.date DESC
               """)
           Page<Routine> findByExerciseName(
               @Param("memberId") Long memberId,
               @Param("exerciseName") String exerciseName,
               @Param("start") LocalDate start,
               @Param("end") LocalDate end,
               Pageable pageable
           );
}

