package com.backend.service.meal.vision;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.AiMealResponseDto;
import com.backend.dto.meal.AiMealVisionFollowupDto;
import com.backend.dto.meal.MealDto;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.context.MealAiContextService;
import com.backend.service.meal.intake.MealIntakeService;
import com.backend.service.meal.ws.MealWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealVisionServiceImpl implements MealVisionService {

    private final AiMealClient aiMealClient;
    private final MealAiContextService mealAiContextService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MealSearch mealSearch;
    private final MealRepository mealRepository;
    private final MealIntakeService mealIntakeService;
    private final MealWsPublisher mealWsPublisher;

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncVisionAnalysis(Long userId, String base64Image) {
        log.info("[Async] Vision AI 분석 요청 - User: {}", userId);

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("ANALYZE_IMAGE")
                .foodImageBase64(base64Image)
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    // pending 저장(후속 자연어가 들어오면 meal-command가 VISION_*를 결정)
                    try {
                        Map<String, Object> analyzedFood = _mapAnalyzedFood(response);
                        Map<String, Object> pendingData = new HashMap<>();
                        pendingData.put("analyzedFood", analyzedFood);
                        pendingData.put("defaultDate", LocalDate.now().toString());
                        pendingData.put("defaultMealTime", _inferMealTimeFromNow().name());
                        mealAiContextService.setPending(userId, "VISION_FOLLOWUP", pendingData);
                    } catch (Exception ignored) {
                        // pending 저장 실패는 UX 치명도가 낮으므로 무시 (WS 결과는 계속 전달)
                    }

                    Object payload = (response != null && response.getAnalyzedFood() != null)
                            ? response.getAnalyzedFood()
                            : Map.of("foodName", "알 수 없음", "calories", 0, "carbs", 0, "protein", 0, "fat", 0);

                    messagingTemplate.convertAndSend("/topic/meal/vision/" + userId, payload);
                    log.info("[Async] Vision 분석 결과 전송 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] Vision 분석 실패: ", throwable);
                    messagingTemplate.convertAndSend("/topic/meal/error/" + userId, "이미지 분석 중 시스템 오류가 발생했습니다.");
                    return null;
                });
    }

    @Override
    public CompletableFuture<AiMealVisionFollowupDto.Response> visionFollowup(Long userId, AiMealVisionFollowupDto.Request request) {
        return aiMealClient.sendVisionFollowupAsync(request)
                .exceptionally(ex -> {
                    log.warn("[MealVision] Vision followup failed: {}", ex.getMessage());
                    return new AiMealVisionFollowupDto.Response("ASK", null, "추가할까요, 변경할까요?");
                });
    }

    @Override
    @Transactional
    public String applyVisionAdd(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        if (userId == null || date == null || analyzedFood == null || analyzedFood.isEmpty()) {
            return "이미지 분석 결과를 반영할 수 없어요. 다시 시도해주세요.";
        }

        Meal.MealTime mealTime = _safeMealTime(mealTimeOrNull);

        String foodName = String.valueOf(analyzedFood.getOrDefault("foodName", "알 수 없음"));
        Integer calories = _intValue(analyzedFood.get("calories"));
        Integer carbs = _intValue(analyzedFood.get("carbs"));
        Integer protein = _intValue(analyzedFood.get("protein"));
        Integer fat = _intValue(analyzedFood.get("fat"));

        MealDto dto = MealDto.builder()
                .foodName(foodName)
                .calories(calories)
                .carbs(carbs)
                .protein(protein)
                .fat(fat)
                .mealDate(date)
                .mealTime(mealTime != null ? mealTime.name() : "DINNER")
                .status(Meal.MealStatus.EATEN.name())
                .isAdditional(true)
                .build();

        mealIntakeService.registerAdditionalMeal(userId, dto);
        mealWsPublisher.publishMealChangedAfterCommit(userId);
        return foodName + "을(를) 추가 섭취로 기록했어요.";
    }

    @Override
    @Transactional
    public String applyVisionReplace(Long userId, LocalDate date, String mealTimeOrNull, Map<String, Object> analyzedFood) {
        if (userId == null || date == null || analyzedFood == null || analyzedFood.isEmpty()) {
            return "이미지 분석 결과를 반영할 수 없어요. 다시 시도해주세요.";
        }

        Meal.MealTime mt = _safeMealTime(mealTimeOrNull);
        if (mt == null) {
            mt = _inferMealTimeFromNow();
        }
        final Meal.MealTime mealTime = mt;

        String foodName = String.valueOf(analyzedFood.getOrDefault("foodName", "알 수 없음"));
        Integer calories = _intValue(analyzedFood.get("calories"));
        Integer carbs = _intValue(analyzedFood.get("carbs"));
        Integer protein = _intValue(analyzedFood.get("protein"));
        Integer fat = _intValue(analyzedFood.get("fat"));

        // 정책(요구사항):
        // - REPLACE는 "계획(PLANNED) 유무/추가(isAdditional) 여부"를 따지지 않습니다.
        // - 같은 끼니의 EATEN은 보존하고, 그 외(PLANNED/SKIPPED 등)는 모두 "교체로 제외" 처리합니다.
        Instant now = Instant.now();

        List<Meal> timeMeals = mealSearch.findMealsByDateAndUser(userId, date).stream()
                .filter(m -> m.getMealTime() == mealTime)
                .toList();

        for (Meal m : timeMeals) {
            if (m.getStatus() == Meal.MealStatus.EATEN) continue; // eaten은 그대로 둔다
            m.changeStatus(Meal.MealStatus.SKIPPED);
            m.markChanged(Meal.MealChanged.REPLACED_OUT, now);
        }

        // 교체로 반영된 음식은 "추가 섭취"가 아니라 해당 끼니의 결과로 기록 (isAdditional=false)
        Meal replacedIn = Meal.builder()
                .userId(userId)
                .mealDate(date)
                .mealTime(mealTime)
                .status(Meal.MealStatus.EATEN)
                .isAdditional(false)
                .foodName(foodName)
                .servingSize(null)
                .calories(calories)
                .carbs(carbs)
                .protein(protein)
                .fat(fat)
                .originalFoodName(foodName)
                .originalServingSize(null)
                .originalCalories(calories)
                .originalCarbs(carbs)
                .originalProtein(protein)
                .originalFat(fat)
                .changed(Meal.MealChanged.REPLACED_IN)
                .changedAt(now)
                .build();
        mealRepository.save(replacedIn);

        mealWsPublisher.publishMealChangedAfterCommit(userId);
        return foodName + "으로 " + mealTime.getLabel() + "를 대체했어요.";
    }

    private static Meal.MealTime _safeMealTime(String mealTimeOrNull) {
        if (mealTimeOrNull == null || mealTimeOrNull.isBlank()) return null;
        try {
            return Meal.MealTime.valueOf(mealTimeOrNull.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Meal.MealTime _inferMealTimeFromNow() {
        // 05:00-11:59 breakfast, 12:00-16:59 lunch, 17:00-04:59 dinner
        LocalTime now = LocalTime.now();
        int mins = now.getHour() * 60 + now.getMinute();
        if (mins >= 300 && mins <= 719) return Meal.MealTime.BREAKFAST;
        if (mins >= 720 && mins <= 1019) return Meal.MealTime.LUNCH;
        return Meal.MealTime.DINNER;
    }

    private static Integer _intValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> _mapAnalyzedFood(AiMealResponseDto response) {
        String foodName = "알 수 없음";
        int calories = 0, carbs = 0, protein = 0, fat = 0;
        if (response != null && response.getAnalyzedFood() != null) {
            AiMealResponseDto.AnalyzedFood af = response.getAnalyzedFood();
            foodName = af.getFoodName() != null ? af.getFoodName() : "알 수 없음";
            calories = af.getCalories() != null ? af.getCalories() : 0;
            carbs = af.getCarbs() != null ? af.getCarbs() : 0;
            protein = af.getProtein() != null ? af.getProtein() : 0;
            fat = af.getFat() != null ? af.getFat() : 0;
        }
        Map<String, Object> analyzedFood = new HashMap<>();
        analyzedFood.put("foodName", foodName);
        analyzedFood.put("calories", calories);
        analyzedFood.put("carbs", carbs);
        analyzedFood.put("protein", protein);
        analyzedFood.put("fat", fat);
        return analyzedFood;
    }
}


