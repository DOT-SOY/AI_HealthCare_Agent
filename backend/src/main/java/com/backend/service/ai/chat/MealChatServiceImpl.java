package com.backend.service.ai.chat;

import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealDto;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.meal.MealService;
import com.backend.service.member.CurrentMemberService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * MEAL_QUERY 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MealChatServiceImpl implements MealChatService {

    private final MealService mealService;
    private final CurrentMemberService currentMemberService;
    private final GeneralChatService generalChatService;

    @Override
    public AIChatResponse handleMeal(IntentClassificationResult classification) {
        String action = classification.getAction();
        
        if (action == null) {
            log.warn("MEAL_QUERY intent에서 action이 null입니다. 일반 채팅으로 처리");
            return generalChatService.handleGeneralChat(classification);
        }

        return switch (action.toUpperCase()) {
            case "QUERY" -> handleMealQuery(classification);
            case "RECOMMEND" -> handleMealRecommend(classification);
            case "MODIFY" -> handleMealModify(classification);
            default -> {
                log.info("MEAL_QUERY intent에서 지원하지 않는 action: {}, 일반 채팅으로 처리", action);
                yield generalChatService.handleGeneralChat(classification);
            }
        };
    }

    /**
     * MEAL의 QUERY 액션 처리 (소분류: action)
     * 
     * - entities에서 date, meal_time 추출
     * - MealService를 통해 식단 조회
     * - 조회 결과를 자연어 메시지로 포맷팅
     */
    private AIChatResponse handleMealQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;
        Object mealTimeObj = entities != null ? entities.get("meal_time") : null;

        LocalDate targetDate = AIChatUtils.resolveDate(dateObj);
        Meal.MealTime mealTime = AIChatUtils.parseMealTime(mealTimeObj);

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        List<MealDto> meals = mealService.getMealsByDateAndTime(memberId, targetDate, mealTime);

        String message = formatMealMessage(meals, targetDate, mealTime);

        return AIChatResponse.builder()
            .message(message)
            .intent("MEAL_QUERY")
            .data(meals)
            .build();
    }

    /**
     * MEAL의 RECOMMEND 액션 처리 (소분류: action)
     * 
     * - 사용자의 상태, 목표, 과거 식단 등을 분석하여 식단 추천
     * - 추후 구현 예정
     */
    private AIChatResponse handleMealRecommend(IntentClassificationResult classification) {
        // TODO: 추후 구현
        log.info("MEAL RECOMMEND 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("식단 추천 기능은 곧 제공될 예정입니다.")
            .intent("MEAL_QUERY")
            .build();
    }

    /**
     * MEAL의 MODIFY 액션 처리 (소분류: action)
     * 
     * - 식단 수정, 메뉴 변경, 영양소 조정 등
     * - 추후 구현 예정
     */
    private AIChatResponse handleMealModify(IntentClassificationResult classification) {
        // TODO: 추후 구현
        log.info("MEAL MODIFY 요청 (추후 구현): {}", classification);
        
        return AIChatResponse.builder()
            .message("식단 수정 기능은 곧 제공될 예정입니다.")
            .intent("MEAL_QUERY")
            .build();
    }

    /**
     * 식단 조회 결과를 자연어 메시지로 포맷팅
     */
    private String formatMealMessage(List<MealDto> meals, LocalDate date, Meal.MealTime mealTime) {
        StringBuilder sb = new StringBuilder();
        String dateStr = AIChatUtils.formatDateForMessage(date);

        if (meals.isEmpty()) {
            if (mealTime != null) {
                sb.append(dateStr).append(" ").append(mealTime.getLabel()).append(" 식단이 등록되어 있지 않아요.");
                sb.append(" 건강한 식단을 계획해보시는 건 어떨까요? 🥗");
            } else {
                sb.append(dateStr).append(" 식단이 등록되어 있지 않아요.");
                sb.append(" 오늘 하루 식단을 기록해보시면 영양 관리를 더 체계적으로 할 수 있어요! 📝");
            }
            return sb.toString();
        }

        if (mealTime != null) {
            // 특정 식사 시간만 조회한 경우
            sb.append(dateStr).append(" ").append(mealTime.getLabel()).append(" 식단을 확인했어요!\n\n");
            MealDto meal = meals.get(0);
            formatSingleMeal(sb, meal);
        } else {
            // 하루 전체 조회한 경우
            sb.append(dateStr).append(" 하루 식단을 확인했어요!\n\n");
            
            // 식사 시간별로 그룹화
            Map<Meal.MealTime, List<MealDto>> mealsByTime = meals.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> Meal.MealTime.valueOf(m.getMealTime())));
            
            for (Meal.MealTime time : new Meal.MealTime[]{Meal.MealTime.BREAKFAST, Meal.MealTime.LUNCH, Meal.MealTime.DINNER}) {
                List<MealDto> timeMeals = mealsByTime.get(time);
                if (timeMeals != null && !timeMeals.isEmpty()) {
                    String timeEmoji = switch (time) {
                        case BREAKFAST -> "🌅";
                        case LUNCH -> "☀️";
                        case DINNER -> "🌙";
                        case SNACK -> "🍪";
                    };
                    sb.append(timeEmoji).append(" ").append(time.getLabel()).append("\n");
                    for (MealDto meal : timeMeals) {
                        formatSingleMeal(sb, meal);
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 단일 식단 정보를 포맷팅
     */
    private void formatSingleMeal(StringBuilder sb, MealDto meal) {
        if (meal.getFoodName() != null && !meal.getFoodName().trim().isEmpty()) {
            // 상태에 따른 이모지
            String statusEmoji = "🍽️";
            if (meal.getStatus() != null) {
                statusEmoji = switch (meal.getStatus().toUpperCase()) {
                    case "EATEN" -> "✅";
                    case "SKIPPED" -> "⏭️";
                    default -> "📋";
                };
            }
            
            sb.append(statusEmoji).append(" ").append(meal.getFoodName());
            if (meal.getServingSize() != null && !meal.getServingSize().trim().isEmpty()) {
                sb.append(" (").append(meal.getServingSize()).append(")");
            }
            sb.append("\n");
            
            // 영양소 정보
            if (meal.getCalories() != null || meal.getProtein() != null || meal.getCarbs() != null || meal.getFat() != null) {
                sb.append("   💊 영양소: ");
                List<String> nutrients = new java.util.ArrayList<>();
                if (meal.getCalories() != null) {
                    nutrients.add("칼로리 " + meal.getCalories() + "kcal");
                }
                if (meal.getProtein() != null) {
                    nutrients.add("단백질 " + meal.getProtein() + "g");
                }
                if (meal.getCarbs() != null) {
                    nutrients.add("탄수화물 " + meal.getCarbs() + "g");
                }
                if (meal.getFat() != null) {
                    nutrients.add("지방 " + meal.getFat() + "g");
                }
                sb.append(String.join(" • ", nutrients)).append("\n");
            }
        } else {
            sb.append("📝 식단 정보가 없어요\n");
        }
    }
}

