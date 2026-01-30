package com.backend.util;

import com.backend.domain.meal.Meal;
import com.backend.domain.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * AI 채팅 관련 유틸리티 클래스
 */
@Slf4j
public class AIChatUtils {

    /**
     * entities.date 값을 LocalDate로 변환합니다.
     * - "today" 또는 null: 오늘 날짜
     * - "YYYY-MM-DD" 형식 문자열: 해당 날짜
     * - 그 외: 오늘 날짜 (fallback)
     */
    public static java.time.LocalDate resolveDate(Object dateObj) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (dateObj == null) {
            return today;
        }
        if (dateObj instanceof String dateStr) {
            String trimmed = dateStr.trim();
            if (trimmed.equalsIgnoreCase("today") || trimmed.isEmpty()) {
                return today;
            }
            try {
                return java.time.LocalDate.parse(trimmed);
            } catch (Exception e) {
                log.warn("날짜 파싱 실패, today로 대체: {}", trimmed);
                return today;
            }
        }
        return today;
    }

    /**
     * 날짜를 사용자 친화적인 메시지 형식으로 변환합니다.
     */
    public static String formatDateForMessage(java.time.LocalDate date) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (date.equals(today)) {
            return "오늘";
        } else if (date.equals(today.minusDays(1))) {
            return "어제";
        } else if (date.equals(today.minusDays(2))) {
            return "그저께";
        } else {
            return date.toString();
        }
    }

    /**
     * entities에서 intensity 값을 추출합니다.
     */
    public static int extractIntensity(Map<String, Object> entities) {
        Object intensityObj = entities.get("intensity");
        if (intensityObj instanceof Number) {
            return ((Number) intensityObj).intValue();
        }
        if (intensityObj instanceof String) {
            try {
                return Integer.parseInt((String) intensityObj);
            } catch (NumberFormatException e) {
                log.warn("intensity 파싱 실패: {}", intensityObj);
            }
        }
        return 5; // 기본값
    }

    /**
     * meal_time 문자열을 Meal.MealTime enum으로 변환
     */
    public static Meal.MealTime parseMealTime(Object mealTimeObj) {
        if (mealTimeObj == null) {
            return null;
        }
        try {
            return Meal.MealTime.valueOf(mealTimeObj.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("meal_time 파싱 실패: {}", mealTimeObj);
            return null;
        }
    }

    /**
     * delivery_status 문자열을 OrderStatus enum으로 변환
     */
    public static OrderStatus parseOrderStatus(Object statusObj) {
        if (statusObj == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(statusObj.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("delivery_status 파싱 실패: {}", statusObj);
            return null;
        }
    }
}

