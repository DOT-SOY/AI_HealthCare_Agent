package com.backend.util;

import com.backend.domain.meal.Meal;
import com.backend.domain.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
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
     * - "5", "6", "5일", "6일": 이번 달 5일, 6일 (요일 맞바꾸기용)
     * - 그 외: 오늘 날짜 (fallback)
     */
    public static LocalDate resolveDate(Object dateObj) {
        LocalDate today = LocalDate.now();
        if (dateObj == null) {
            return today;
        }
        if (dateObj instanceof Number num) {
            int day = num.intValue();
            if (day >= 1 && day <= 31) {
                return toDateInCurrentMonth(day);
            }
            return today;
        }
        if (dateObj instanceof String dateStr) {
            String trimmed = dateStr.trim();
            if (trimmed.equalsIgnoreCase("today") || trimmed.isEmpty()) {
                return today;
            }
            try {
                return LocalDate.parse(trimmed);
            } catch (Exception e) {
                // "5일", "6" 등 → 이번 달 n일
                Integer dayOfMonth = parseDayOfMonth(trimmed);
                if (dayOfMonth != null) {
                    return toDateInCurrentMonth(dayOfMonth);
                }
                log.warn("날짜 파싱 실패, today로 대체: {}", trimmed);
                return today;
            }
        }
        return today;
    }

    /** "5", "5일", "15" 등에서 일(day)만 추출. 1~31이면 반환, 아니면 null */
    public static Integer parseDayOfMonth(String s) {
        if (s == null || s.isEmpty()) return null;
        String t = s.trim().replaceAll("일$", "");
        try {
            int day = Integer.parseInt(t);
            return (day >= 1 && day <= 31) ? day : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate toDateInCurrentMonth(int day) {
        LocalDate today = LocalDate.now();
        int maxDay = today.lengthOfMonth();
        int safeDay = Math.min(day, maxDay);
        return today.withDayOfMonth(safeDay);
    }

    /**
     * 요일 맞바꾸기용: date1, date2를 해석. "5일", "6일" → 이번 달 5일, 6일.
     */
    public static LocalDate resolveDateForSwap(Object dateObj) {
        if (dateObj == null) return LocalDate.now();
        if (dateObj instanceof Number num) {
            int day = num.intValue();
            if (day >= 1 && day <= 31) return toDateInCurrentMonth(day);
            return LocalDate.now();
        }
        if (dateObj instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return LocalDate.now();
            try {
                return LocalDate.parse(trimmed);
            } catch (Exception e) {
                Integer day = parseDayOfMonth(trimmed);
                if (day != null) return toDateInCurrentMonth(day);
            }
        }
        return LocalDate.now();
    }

    /**
     * 날짜를 사용자 친화적인 메시지 형식으로 변환합니다.
     */
    public static String formatDateForMessage(LocalDate date) {
        LocalDate today = LocalDate.now();
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

