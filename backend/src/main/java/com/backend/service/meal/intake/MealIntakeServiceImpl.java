package com.backend.service.meal.intake;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealDto;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.ws.MealWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealIntakeServiceImpl implements MealIntakeService {

    private final MealRepository mealRepository;
    private final MealSearch mealSearch;
    private final MealWsPublisher mealWsPublisher;

    @Override
    @Transactional
    public MealDto registerAdditionalMeal(Long userId, MealDto mealDto) {
        log.info("[Meal] 추가 식단 등록 - User: {}, Food: {}", userId, mealDto.getFoodName());
        mealDto.setIsAdditional(true);
        mealDto.setStatus(Meal.MealStatus.EATEN.name());
        Meal saved = mealRepository.save(mealDto.toEntity(userId));
        mealWsPublisher.publishMealChangedAfterCommit(userId);
        return MealDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public MealDto updateMeal(Long scheduleId, MealDto mealDto) {
        log.info("[Meal] 식단 정보 수정 - ID: {}", scheduleId);
        Meal meal = mealRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("해당 식단 데이터를 찾을 수 없습니다."));

        // Original은 보존, 실측값만 업데이트하여 분석 근거 유지
        meal.updateMealInfo(
                mealDto.getFoodName(), mealDto.getServingSize(),
                mealDto.getCalories(), mealDto.getCarbs(),
                mealDto.getProtein(), mealDto.getFat(),
                Meal.MealStatus.valueOf(mealDto.getStatus())
        );
        mealWsPublisher.publishMealChangedAfterCommit(meal.getUserId());
        return MealDto.fromEntity(meal);
    }

    @Override
    @Transactional
    public void toggleMealStatus(Long scheduleId, String status) {
        mealRepository.findById(scheduleId).ifPresent(m -> {
            log.info("[Meal] 상태 변경 - ID: {}, Status: {}", scheduleId, status);
            m.changeStatus(Meal.MealStatus.valueOf(status));
            // [중요] 식단 변경 사항 전파 (프론트 자동 갱신용)
            mealWsPublisher.publishMealChangedAfterCommit(m.getUserId());
        });
    }

    @Override
    @Transactional
    public void removeOrSkipMeal(Long scheduleId, boolean isPermanentDelete) {
        mealRepository.findById(scheduleId).ifPresent(m -> {
            if (isPermanentDelete || m.getIsAdditional()) {
                log.info("[Meal] 데이터 영구 삭제 - ID: {}", scheduleId);
                mealRepository.delete(m);
            } else {
                log.info("[Meal] 계획 식단 건너뛰기 처리 - ID: {}", scheduleId);
                m.changeStatus(Meal.MealStatus.SKIPPED);
            }
            mealWsPublisher.publishMealChangedAfterCommit(m.getUserId());
        });
    }

    @Override
    @Transactional
    public String toggleMealTimeComplete(Long userId, LocalDate date, String mealTime) {
        if (userId == null || date == null || mealTime == null || mealTime.isBlank()) {
            return "처리할 날짜/끼니 정보가 부족해요. (예: '오늘 점심 완료')";
        }
        Meal.MealTime mt;
        try {
            mt = Meal.MealTime.valueOf(mealTime.trim().toUpperCase());
        } catch (Exception e) {
            return "끼니를 이해하지 못했어요. (아침/점심/저녁 중 선택)";
        }

        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getMealTime() == mt)
                .toList();

        List<Meal> planned = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .toList();
        List<Meal> eatenNotAdditional = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN && (m.getIsAdditional() == null || !m.getIsAdditional()))
                .toList();

        if (!planned.isEmpty()) {
            planned.forEach(m -> m.changeStatus(Meal.MealStatus.EATEN));
            mealWsPublisher.publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니를 완료 처리했어요.";
        }
        if (!eatenNotAdditional.isEmpty()) {
            eatenNotAdditional.forEach(m -> m.changeStatus(Meal.MealStatus.PLANNED));
            mealWsPublisher.publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니 완료를 취소했어요.";
        }
        return mt.getLabel() + "에 완료/계획 처리할 항목이 없어요.";
    }

    @Override
    @Transactional
    public String toggleMealTimeSkip(Long userId, LocalDate date, String mealTime) {
        if (userId == null || date == null || mealTime == null || mealTime.isBlank()) {
            return "처리할 날짜/끼니 정보가 부족해요. (예: '오늘 점심 생략')";
        }
        Meal.MealTime mt;
        try {
            mt = Meal.MealTime.valueOf(mealTime.trim().toUpperCase());
        } catch (Exception e) {
            return "끼니를 이해하지 못했어요. (아침/점심/저녁 중 선택)";
        }

        // 재배분으로 생성된 "추가 메뉴(PLANNED, isAdditional=true)"는
        // - 생략 시점/취소 시점에 모두 정리하여 일관성을 유지합니다.
        List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> additionalPlannedToDelete = dayMeals.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsAdditional()))
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .toList();
        if (!additionalPlannedToDelete.isEmpty()) {
            mealRepository.deleteAll(additionalPlannedToDelete);
            mealRepository.flush();
        }

        List<Meal> meals = dayMeals.stream()
                .filter(m -> m.getMealTime() == mt)
                .toList();

        List<Meal> planned = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                .toList();
        List<Meal> skippedNotAdditional = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED && (m.getIsAdditional() == null || !m.getIsAdditional()))
                .toList();

        if (!planned.isEmpty()) {
            // changed 컬럼 도입 이후에는 "교체로 밀려난 잔재"는 REPLACED_OUT으로 구분되며,
            // 대시보드 조립 단계에서 화면에서 숨깁니다. 따라서 여기서 SKIPPED를 통째로 삭제하면
            // 사용자 메뉴별 생략(SKIPPED)의 취소 흐름이 깨질 수 있어 삭제 로직을 제거합니다.

            planned.forEach(m -> m.changeStatus(Meal.MealStatus.SKIPPED));
            mealWsPublisher.publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니를 생략 처리했어요.";
        }
        if (!skippedNotAdditional.isEmpty()) {
            skippedNotAdditional.forEach(m -> m.changeStatus(Meal.MealStatus.PLANNED));
            mealWsPublisher.publishMealChangedAfterCommit(userId);
            return mt.getLabel() + " 끼니 생략을 취소했어요.";
        }
        return mt.getLabel() + "에 생략/계획 처리할 항목이 없어요.";
    }

    @Override
    @Transactional
    public String toggleItemByFoodName(Long userId, LocalDate date, String mealTimeOrNull, String foodName, String mode) {
        if (userId == null || date == null || foodName == null || foodName.isBlank()) {
            return "처리할 음식명이 필요해요. (예: '오늘 점심 칼국수 생략')";
        }
        String target = foodName.trim();
        Meal.MealTime mt = null;
        if (mealTimeOrNull != null && !mealTimeOrNull.isBlank()) {
            try {
                mt = Meal.MealTime.valueOf(mealTimeOrNull.trim().toUpperCase());
            } catch (Exception ignored) {
                mt = null;
            }
        }
        final Meal.MealTime mtFinal = mt;

        List<Meal> meals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> mtFinal == null || m.getMealTime() == mtFinal)
                .toList();

        List<Meal> candidates = meals.stream()
                .filter(m -> m.getFoodName() != null && m.getFoodName().toLowerCase().contains(target.toLowerCase()))
                .toList();

        if (candidates.isEmpty()) {
            return "해당 음식('" + target + "')을(를) 찾지 못했어요. 음식명을 조금 더 정확히 말해줘요.";
        }
        if (candidates.size() > 1) {
            return "같은 이름의 항목이 여러 개 있어요. 끼니(아침/점심/저녁)까지 함께 말해줘요.";
        }

        Meal m = candidates.get(0);
        String mMode = mode == null ? "COMPLETE" : mode.trim().toUpperCase();
        if ("SKIP".equals(mMode)) {
            Meal.MealStatus next = (m.getStatus() == Meal.MealStatus.SKIPPED) ? Meal.MealStatus.PLANNED : Meal.MealStatus.SKIPPED;
            m.changeStatus(next);
            mealWsPublisher.publishMealChangedAfterCommit(userId);
            return "'" + m.getFoodName() + "' 항목을 " + (next == Meal.MealStatus.SKIPPED ? "생략" : "생략취소") + " 처리했어요.";
        }
        // COMPLETE
        Meal.MealStatus next = (m.getStatus() == Meal.MealStatus.EATEN) ? Meal.MealStatus.PLANNED : Meal.MealStatus.EATEN;
        m.changeStatus(next);
        mealWsPublisher.publishMealChangedAfterCommit(userId);
        return "'" + m.getFoodName() + "' 항목을 " + (next == Meal.MealStatus.EATEN ? "완료" : "완료취소") + " 처리했어요.";
    }
}


