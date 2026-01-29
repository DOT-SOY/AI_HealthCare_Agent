package com.backend.repository.meal;

import com.backend.domain.meal.Meal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealRepository extends JpaRepository<Meal, Long> {

    // 1. 해당 날짜의 모든 식단 가져오기 (기존 유지)
    List<Meal> findByUserIdAndMealDate(Long userId, LocalDate mealDate);

    // 2. [필수] 특정 날짜의 '특정 끼니(예: 점심)' 하나만 콕 집어 가져오기
    // 용도: 수정/삭제 시 아침,저녁 다 불러올 필요 없이 얘만 딱 불러오려고 사용함. (성능 최적화)
    Optional<Meal> findByUserIdAndMealDateAndMealTime(Long userId, LocalDate mealDate, Meal.MealTime mealTime);

    // 3. 회원 탈퇴 시 전체 삭제 (기존 유지)
    void deleteByUserId(Long userId);
}

