package com.backend.service.ai.chat;

import com.backend.client.meal.MealCommandClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.MealAiContextDto;
import com.backend.dto.meal.MealCommandResponseDto;
import com.backend.dto.meal.MealDto;
import com.backend.dto.request.AIChatRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.context.MealAiContextService;
import com.backend.service.meal.MealService;
import com.backend.service.member.CurrentMemberService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MEAL_QUERY 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MealChatServiceImpl implements MealChatService {

    private final MealService mealService;
    private final CurrentMemberService currentMemberService;
    private final MealCommandClient mealCommandClient;
    private final MealAiContextService mealAiContextService;
    private final MealSearch mealSearch;
    // 조회가 아닌 요청은 meal-command 흐름으로 처리하며, 일반 채팅으로의 fallback은 상위 오케스트레이터에서 담당합니다.

    @Override
    public AIChatResponse handleMeal(IntentClassificationResult classification, AIChatRequest request) {
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        MealAiContextDto preCtx = null;
        try {
            preCtx = mealAiContextService.get(memberId);
        } catch (Exception ignored) {
            preCtx = null;
        }

        String pendingType = preCtx != null && preCtx.getPending() != null ? preCtx.getPending().getType() : null;
        boolean hasPending = pendingType != null && !pendingType.isBlank();

        // 1) pending이 있는 상태에서는 "후속 응답"으로 처리하는 것이 우선입니다.
        // 특히 VISION_FOLLOWUP(이미지 분석 후 반영 여부 질문)에서 "점심이야" 같은 응답이 조회로 빠지면 UX가 깨집니다.
        boolean shouldBypassQuery = hasPending && (
                "VISION_FOLLOWUP".equalsIgnoreCase(pendingType)
                        || "OVERLAP_STRATEGY".equalsIgnoreCase(pendingType)
                        || "ASK_CLARIFY".equalsIgnoreCase(pendingType)
        );
        if (!shouldBypassQuery) {
            // 조회는 "명확한 질문"일 때만 (생성/교체 요청이 QUERY로 오분류되는 문제 방지)
            if (_isExplicitMealQuery(classification, request)) {
                return handleMealQuery(classification);
            }
        }

        // 2) 나머지는 meal-command(LLM)이 operation을 결정 → 백엔드는 실행만 담당
        String originalText = request != null ? request.getText() : null;
        if (originalText == null || originalText.isBlank()) {
            return AIChatResponse.builder()
                    .message("식단 관련 요청을 이해하지 못했어요. 다시 말씀해주세요.")
                    .intent("MEAL_QUERY")
                    .build();
        }

        // meal-only 컨텍스트 저장
        MealAiContextDto ctx = mealAiContextService.appendUser(memberId, originalText);

        MealCommandResponseDto cmd;
        try {
            cmd = mealCommandClient.resolveCommand(originalText, ctx);
        } catch (Exception e) {
            log.warn("[MEAL] meal-command 추론 실패. fallback로 ASK_CLARIFY: {}", e.getMessage());
            cmd = MealCommandResponseDto.builder()
                    .operation("ASK_CLARIFY")
                    .clarifyingQuestion("식단 요청을 처리하는 중 오류가 발생했어요.\n기간을 포함해 다시 말해줘요. (예: '오늘부터 7일치 식단')")
                    .confidence(0.0)
                    .build();
        }

        String op = cmd.getOperation() != null ? cmd.getOperation().trim().toUpperCase() : "ASK_CLARIFY";
        MealAiContextDto.Pending pending = ctx != null ? ctx.getPending() : null;

        // REPLAN
        if ("REPLAN".equals(op)) {
            LocalDate targetDate = _parseIsoOrToday(cmd.getTargetDate());
            // 정책: 재정비는 기본적으로 오늘 (과거 방지)
            if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();
            mealService.asyncMealReplan(memberId, targetDate);
            String msg = "식단 재정비를 시작했어요. 잠시만 기다려주세요...";
            mealAiContextService.appendAssistant(memberId, msg);
            mealAiContextService.clearPending(memberId);
            return AIChatResponse.builder()
                    .message(msg)
                    .intent("MEAL_QUERY")
                    .data(Map.of("operation", "REPLAN", "date", String.valueOf(targetDate)))
                    .build();
        }

        // Vision followup
        if ("VISION_ADD".equals(op) || "VISION_REPLACE".equals(op) || "VISION_CANCEL".equals(op)) {
            return handleVisionFollowup(cmd, pending, memberId, op);
        }

        // 끼니/항목 토글
        if ("MEALTIME_COMPLETE_TOGGLE".equals(op)) {
            return handleMealTimeComplete(cmd, memberId);
        }
        if ("MEALTIME_SKIP_TOGGLE".equals(op)) {
            return handleMealTimeSkip(cmd, memberId);
        }
        if ("ITEM_COMPLETE_TOGGLE".equals(op) || "ITEM_SKIP_TOGGLE".equals(op)) {
            return handleItemToggle(cmd, memberId, op);
        }

        // ASK_CLARIFY
        if ("ASK_CLARIFY".equals(op)) {
            return handleAskClarify(cmd, pending, memberId);
        }

        // GENERATE 계열
        return handleGenerate(cmd, classification, pending, memberId, op);
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

    private boolean _isExplicitMealQuery(IntentClassificationResult classification, AIChatRequest request) {
        String action = classification != null ? classification.getAction() : null;
        if (action == null || !action.trim().equalsIgnoreCase("QUERY")) return false;

        String text = request != null ? request.getText() : null;
        if (text == null) return false;
        String t = text.replaceAll("\\s+", "");

        // 생성/교체/추가/재정비 등은 조회로 보지 않음
        boolean looksLikeCommand = t.contains("짜") || t.contains("생성") || t.contains("만들") || t.contains("계획")
                || t.contains("추천") || t.contains("바꿔") || t.contains("교체") || t.contains("추가")
                || t.contains("재정비") || t.contains("replan");
        if (looksLikeCommand) return false;

        // 조회 표현이 있을 때만 true
        return t.contains("뭐먹") || t.contains("뭐먹었") || t.contains("뭐야") || t.contains("언제먹")
                || t.contains("식단") || t.contains("아침") || t.contains("점심") || t.contains("저녁");
    }

    private AIChatResponse handleVisionFollowup(MealCommandResponseDto cmd, MealAiContextDto.Pending pending, Long memberId, String op) {
        Map<String, Object> pendingData = pending != null ? pending.getData() : null;

        String targetIso = cmd.getTargetDate();
        if ((targetIso == null || targetIso.isBlank()) && pendingData != null && pendingData.get("defaultDate") != null) {
            targetIso = String.valueOf(pendingData.get("defaultDate"));
        }
        LocalDate targetDate = _parseIsoOrToday(targetIso);
        if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();

        String mealTime = cmd.getMealTime();
        if ((mealTime == null || mealTime.isBlank()) && pendingData != null && pendingData.get("defaultMealTime") != null) {
            mealTime = String.valueOf(pendingData.get("defaultMealTime"));
        }

        Map<String, Object> analyzedFood = null;
        if (pendingData != null) {
            Object af = pendingData.get("analyzedFood");
            if (af instanceof Map<?, ?> m) {
                analyzedFood = new java.util.HashMap<>();
                analyzedFood.put("foodName", m.get("foodName"));
                analyzedFood.put("calories", m.get("calories"));
                analyzedFood.put("carbs", m.get("carbs"));
                analyzedFood.put("protein", m.get("protein"));
                analyzedFood.put("fat", m.get("fat"));
            }
        }

        if ("VISION_CANCEL".equals(op)) {
            String msg = "알겠어요. 이번 사진은 반영하지 않을게요.";
            mealAiContextService.appendAssistant(memberId, msg);
            mealAiContextService.clearPending(memberId);
            return AIChatResponse.builder()
                    .message(msg)
                    .intent("MEAL_QUERY")
                    .data(Map.of("operation", op))
                    .build();
        }

        if (analyzedFood == null || analyzedFood.isEmpty()) {
            String msg = "이미지 분석 결과가 세션에 남아있지 않아요. 사진을 다시 올려주실래요?";
            mealAiContextService.appendAssistant(memberId, msg);
            mealAiContextService.clearPending(memberId);
            return AIChatResponse.builder()
                    .message(msg)
                    .intent("MEAL_QUERY")
                    .data(Map.of("operation", "ASK_CLARIFY"))
                    .build();
        }

        String msg;
        if ("VISION_REPLACE".equals(op)) {
            msg = mealService.applyVisionReplace(memberId, targetDate, mealTime, analyzedFood);
        } else {
            msg = mealService.applyVisionAdd(memberId, targetDate, mealTime, analyzedFood);
        }

        // 이미지 반영 후, 필요하면 재정비
        if (Boolean.TRUE.equals(cmd.getAlsoReplan())) {
            mealService.asyncMealReplan(memberId, targetDate);
        }

        mealAiContextService.appendAssistant(memberId, msg);
        mealAiContextService.clearPending(memberId);
        return AIChatResponse.builder()
                .message(msg)
                .intent("MEAL_QUERY")
                .data(Map.of("operation", op, "date", String.valueOf(targetDate)))
                .build();
    }

    private AIChatResponse handleMealTimeComplete(MealCommandResponseDto cmd, Long memberId) {
        LocalDate targetDate = _parseIsoOrToday(cmd.getTargetDate());
        if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();
        String mealTime = cmd.getMealTime();
        String msg = mealService.toggleMealTimeComplete(memberId, targetDate, mealTime);
        if (Boolean.TRUE.equals(cmd.getAlsoReplan())) {
            mealService.asyncMealReplan(memberId, targetDate);
        }
        mealAiContextService.appendAssistant(memberId, msg);
        mealAiContextService.clearPending(memberId);
        return AIChatResponse.builder()
                .message(msg)
                .intent("MEAL_QUERY")
                .data(Map.of("operation", "MEALTIME_COMPLETE_TOGGLE", "date", String.valueOf(targetDate), "mealTime", String.valueOf(mealTime)))
                .build();
    }

    private AIChatResponse handleMealTimeSkip(MealCommandResponseDto cmd, Long memberId) {
        LocalDate targetDate = _parseIsoOrToday(cmd.getTargetDate());
        if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();
        String mealTime = cmd.getMealTime();
        String msg = mealService.toggleMealTimeSkip(memberId, targetDate, mealTime);
        if (Boolean.TRUE.equals(cmd.getAlsoReplan())) {
            mealService.asyncRedistributeAfterMealTimeSkip(memberId, targetDate, mealTime);
        }
        mealAiContextService.appendAssistant(memberId, msg);
        mealAiContextService.clearPending(memberId);
        return AIChatResponse.builder()
                .message(msg)
                .intent("MEAL_QUERY")
                .data(Map.of("operation", "MEALTIME_SKIP_TOGGLE", "date", String.valueOf(targetDate), "mealTime", String.valueOf(mealTime)))
                .build();
    }

    private AIChatResponse handleItemToggle(MealCommandResponseDto cmd, Long memberId, String op) {
        LocalDate targetDate = _parseIsoOrToday(cmd.getTargetDate());
        if (targetDate.isBefore(LocalDate.now())) targetDate = LocalDate.now();
        String mode = "ITEM_SKIP_TOGGLE".equals(op) ? "SKIP" : "COMPLETE";
        String msg = mealService.toggleItemByFoodName(memberId, targetDate, cmd.getMealTime(), cmd.getFoodName(), mode);
        if (Boolean.TRUE.equals(cmd.getAlsoReplan())) {
            mealService.asyncMealReplan(memberId, targetDate);
        }
        mealAiContextService.appendAssistant(memberId, msg);
        mealAiContextService.clearPending(memberId);
        return AIChatResponse.builder()
                .message(msg)
                .intent("MEAL_QUERY")
                .data(Map.of("operation", op, "date", String.valueOf(targetDate)))
                .build();
    }

    private AIChatResponse handleAskClarify(MealCommandResponseDto cmd, MealAiContextDto.Pending pending, Long memberId) {
        String q = cmd.getClarifyingQuestion();
        if (q == null || q.isBlank()) {
            q = "언제부터, 며칠치 식단을 짜드릴까요?\n예: '오늘부터 7일', '내일부터 2주', '2026-02-10부터 30일(한달)'";
        }

        // pending이 없거나 ASK_CLARIFY일 때만 덮어쓰기
        try {
            String existingPendingType = pending != null ? pending.getType() : null;
            boolean canOverridePending = existingPendingType == null
                    || existingPendingType.isBlank()
                    || "ASK_CLARIFY".equalsIgnoreCase(existingPendingType);
            if (canOverridePending) {
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("need", "PERIOD_DAYS");
                data.put("defaultStartDate", LocalDate.now().toString());
                mealAiContextService.setPending(memberId, "ASK_CLARIFY", data);
            }
        } catch (Exception ignored) {
            // pending 저장 실패는 UX 치명도가 낮으므로 무시
        }

        mealAiContextService.appendAssistant(memberId, q);
        return AIChatResponse.builder()
                .message(q)
                .intent("MEAL_QUERY")
                .data(Map.of("operation", "ASK_CLARIFY"))
                .build();
    }

    private AIChatResponse handleGenerate(MealCommandResponseDto cmd, IntentClassificationResult classification,
                                         MealAiContextDto.Pending pending, Long memberId, String op) {
        Integer periodDays = cmd.getPeriodDays();
        LocalDate startDate = _parseIsoOrToday(cmd.getStartDate());

        // 정책: 과거 시작일 금지 → 오늘로 보정
        LocalDate today = LocalDate.now();
        boolean wasPast = startDate.isBefore(today);
        if (wasPast) startDate = today;

        if (periodDays == null || periodDays <= 0) {
            // meal-command이 periodDays를 못 뽑았으면 ASK_CLARIFY로 전환
            String msg = "언제부터, 며칠치 식단을 짜드릴까요?\n예: '오늘부터 7일', '내일부터 2주', '2026-02-10부터 30일(한달)'";
            mealAiContextService.setPending(memberId, "ASK_CLARIFY", Map.of(
                    "need", "PERIOD_DAYS",
                    "defaultStartDate", today.toString()
            ));
            mealAiContextService.appendAssistant(memberId, msg);
            return AIChatResponse.builder()
                    .message(msg)
                    .intent("MEAL_QUERY")
                    .data(Map.of("operation", "ASK_CLARIFY"))
                    .build();
        }

        LocalDate endDate = startDate.plusDays(Math.max(1, periodDays) - 1L);
        boolean hasExisting = mealSearch.findMealsBetweenDates(memberId, startDate, endDate).stream()
                .anyMatch(m -> m.getStatus() == Meal.MealStatus.PLANNED);

        boolean explicitOverwrite = "GENERATE_OVERWRITE".equals(op);
        boolean explicitFillMissing = "GENERATE_FILL_MISSING".equals(op);

        if (hasExisting && !explicitOverwrite && !explicitFillMissing && "GENERATE".equals(op)) {
            String msg = "요청하신 기간에 이미 설정된 식단이 있어요.\n"
                    + "1) 기간 전체를 새로 짜서 덮어쓰기(기존 계획 삭제)\n"
                    + "2) 겹치는 날짜는 그대로 두고, 비어있는 날짜만 채우기\n"
                    + "원하는 방식으로 답해줘요. (예: '1번', '2번', '덮어써', '기존 유지하고 빈날만')";
            mealAiContextService.setPending(memberId, "OVERLAP_STRATEGY", Map.of(
                    "startDate", String.valueOf(startDate),
                    "periodDays", periodDays
            ));
            mealAiContextService.appendAssistant(memberId, msg);
            return AIChatResponse.builder()
                    .message(msg)
                    .intent("MEAL_QUERY")
                    .data(Map.of(
                            "operation", "ASK_OVERLAP_STRATEGY",
                            "startDate", String.valueOf(startDate),
                            "periodDays", periodDays
                    ))
                    .build();
        }

        // 비동기로 생성 시작 (goalType은 MealServiceImpl에서 보정 가능)
        String goalType = cmd.getGoalType() != null ? cmd.getGoalType() : "MAINTAIN";

        if (explicitFillMissing && hasExisting && !explicitOverwrite) {
            mealService.asyncGeneratePlanFillMissingFromAiChat(memberId, startDate, periodDays, goalType);
        } else {
            mealService.asyncGeneratePlanFromAiChat(memberId, startDate, periodDays, goalType);
        }

        mealAiContextService.clearPending(memberId);
        String msg = (wasPast ? "과거 날짜로는 식단을 생성할 수 없어 오늘부터로 진행할게요.\n\n" : "")
                + "식단 생성 중 (0%)";
        mealAiContextService.appendAssistant(memberId, msg);
        return AIChatResponse.builder()
                .message(msg)
                .intent("MEAL_QUERY")
                .data(Map.of(
                        "operation", explicitFillMissing ? "GENERATE_FILL_MISSING" : (explicitOverwrite ? "GENERATE_OVERWRITE" : "GENERATE"),
                        "startDate", String.valueOf(startDate),
                        "periodDays", periodDays
                ))
                .build();
    }

    private LocalDate _parseIsoOrToday(String iso) {
        if (iso == null) return LocalDate.now();
        String s = iso.trim();
        if (s.isBlank() || "today".equalsIgnoreCase(s)) return LocalDate.now();
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
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
                .collect(Collectors.groupingBy(m -> Meal.MealTime.valueOf(m.getMealTime())));
            
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
                List<String> nutrients = new ArrayList<>();
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
