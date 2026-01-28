package com.backend.repository.meal;

import com.backend.domain.meal.MealTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MealTargetRepository extends JpaRepository<MealTarget, Long> {

    // 1. 특정 날짜의 목표 조회 (오늘 내 목표 칼로리 얼마지?)
    // 중복 데이터가 있을 경우 가장 최신 것만 반환
    Optional<MealTarget> findTopByUserIdAndTargetDateOrderByTargetDateDesc(Long userId, LocalDate targetDate);

    // 2. 가장 최근에 설정했던 목표 가져오기 (혹시 오늘 목표가 없으면 어제 거 베껴오려고)
    Optional<MealTarget> findTopByUserIdOrderByTargetDateDesc(Long userId);

    // 3. 회원 탈퇴 시 전체 삭제
    void deleteByUserId(Long userId);

    boolean existsByUserIdAndTargetDateBetween(Long userId, LocalDate start, LocalDate end);
}