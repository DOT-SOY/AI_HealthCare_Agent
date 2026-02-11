package com.backend.service.meal.replan;

import com.backend.client.meal.AiMealClient;
import com.backend.domain.meal.Meal;
import com.backend.dto.meal.AiMealRequestDto;
import com.backend.dto.meal.MealDto;
import com.backend.dto.meal.MealTargetDto;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.meal.MealSearch;
import com.backend.service.meal.plan.MealPlanService;
import com.backend.service.meal.target.MealTargetService;
import com.backend.service.meal.ws.MealWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class MealReplanServiceImpl implements MealReplanService {

    private final MealSearch mealSearch;
    private final MealRepository mealRepository;
    private final MealTargetService mealTargetService;
    private final MealPlanService mealPlanService;
    private final AiMealClient aiMealClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final MealWsPublisher mealWsPublisher;
    private final com.backend.service.memberinfo.MemberInfoBodyService memberInfoBodyService;
    private final PlatformTransactionManager transactionManager;

    /**
     * [목표치 자동 생성]
     * - 목표가 없으면 중단하지 않고 memberinfo 기반으로 생성합니다.
     */
    @Transactional
    protected MealTargetDto ensureTargetAuto(Long userId, LocalDate date) {
        MealTargetDto existing = mealTargetService.getTargetByDate(userId, date);
        if (existing != null) return existing;

        MemberInfoBodyResponseDTO body = null;
        try {
            body = memberInfoBodyService.getLatest(userId);
        } catch (Exception ignored) {
            body = null;
        }

        String goalType = "MAINTAIN";
        if (body != null && body.getExercisePurpose() != null) {
            goalType = body.getExercisePurpose().name();
        }
        goalType = goalType == null || goalType.isBlank() ? "MAINTAIN" : goalType.trim().toUpperCase();
        if (!goalType.equals("DIET") && !goalType.equals("BULK_UP") && !goalType.equals("MAINTAIN")) {
            goalType = "MAINTAIN";
        }

        Double weight = body != null ? body.getWeight() : null; // kg
        Double height = body != null ? body.getHeight() : null; // cm
        Integer age = _ageFromBirthDate(body != null ? body.getBirthDate() : null);
        String gender = body != null ? body.getGender() : null; // "MALE"/"FEMALE" 예상

        double bmr;
        if (weight != null && height != null && age != null && weight > 0 && height > 0 && age > 0) {
            boolean isMale = gender != null && gender.trim().equalsIgnoreCase("MALE");
            boolean isFemale = gender != null && gender.trim().equalsIgnoreCase("FEMALE");
            double base = (10.0 * weight) + (6.25 * height) - (5.0 * age);
            bmr = base + (isMale ? 5.0 : isFemale ? -161.0 : -78.0);
        } else {
            bmr = 1500.0;
        }

        double tdee = bmr * 1.55;
        double factor = switch (goalType) {
            case "DIET" -> 0.85;
            case "BULK_UP" -> 1.15;
            default -> 1.00;
        };
        int cal = (int) Math.round(tdee * factor);
        cal = Math.max(1200, cal);

        double w = weight != null && weight > 0 ? weight : 70.0;
        double proteinPerKg = switch (goalType) {
            case "DIET" -> 1.8;
            case "BULK_UP" -> 2.0;
            default -> 1.6;
        };
        double fatPerKg = switch (goalType) {
            case "DIET" -> 0.7;
            case "BULK_UP" -> 0.9;
            default -> 0.8;
        };

        int protein = (int) Math.round(w * proteinPerKg);
        int fat = (int) Math.round(w * fatPerKg);
        int remainCal = cal - (protein * 4) - (fat * 9);
        int carbs = Math.max(0, (int) Math.round(remainCal / 4.0));

        MealTargetDto dto = MealTargetDto.builder()
                .targetDate(date)
                .goalType(goalType)
                .goalCal(cal)
                .goalCarbs(carbs)
                .goalProtein(protein)
                .goalFat(fat)
                .build();
        return mealTargetService.updateTarget(userId, dto);
    }

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncMealReplan(Long userId, LocalDate date) {
        log.info("[Async] 식단 재구성(Replan) 시작 - User: {}, Date: {}", userId, date);

        List<Meal> existing = mealSearch.findMealsByDateAndUser(userId, date);
        List<Meal> remainingPlanned = existing.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                .filter(m -> !Boolean.TRUE.equals(m.getIsAdditional()))
                .toList();

        if (remainingPlanned.isEmpty()) {
            String msg = "잔여 식사가 없어 오늘 식사가 여기서 끝난다";
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, msg);
            log.info("[Async] 식단 재구성 건너뜀 - 남은 PLANNED 끼니 없음");
            return CompletableFuture.completedFuture(null);
        }

        List<MealDto> currentMealsForReplan = existing.stream().map(MealDto::fromEntity).toList();
        MealTargetDto remaining = mealTargetService.calculateRemainingNutrients(userId, date);
        if (remaining == null) {
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "오늘 목표치가 없어 재정비를 진행할 수 없습니다.");
            return CompletableFuture.completedFuture(null);
        }

        AiMealRequestDto request = AiMealRequestDto.builder()
                .requestType("REPLAN")
                .goal(AiMealRequestDto.GoalSpec.builder()
                        .targetCalories(remaining.getGoalCal())
                        .targetCarbs(remaining.getGoalCarbs())
                        .targetProtein(remaining.getGoalProtein())
                        .targetFat(remaining.getGoalFat())
                        .build())
                .currentMeals(currentMealsForReplan)
                .build();

        return aiMealClient.sendRequestAsync(request)
                .thenAccept(response -> {
                    List<MealDto> suggested = response != null ? response.getSuggestedMeals() : null;
                    if (suggested == null) suggested = List.of();

                    Set<String> remainingTimes = currentMealsForReplan.stream()
                            .filter(m -> m.getStatus() != null
                                    && m.getStatus().equalsIgnoreCase(Meal.MealStatus.PLANNED.name())
                                    && (m.getIsAdditional() == null || !m.getIsAdditional()))
                            .map(MealDto::getMealTime)
                            .filter(Objects::nonNull)
                            .map(String::toUpperCase)
                            .collect(Collectors.toSet());

                    List<MealDto> filtered = suggested.stream()
                            .filter(m -> m.getMealTime() != null && remainingTimes.contains(m.getMealTime().toUpperCase()))
                            .toList();

                    if (filtered.isEmpty()) {
                        messagingTemplate.convertAndSend("/topic/meal/replan/" + userId,
                                "재정비를 시도했지만 제안할 남은 끼니가 없어요. (이미 모두 완료/생략 상태이거나 제안 생성이 실패했을 수 있어요)");
                        log.warn("[Async] 식단 재구성 결과가 비어있어 updatePlannedMeals를 스킵합니다. userId={}, date={}", userId, date);
                        return;
                    }

                    mealPlanService.updatePlannedMeals(userId, date, filtered);
                    messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "남은 일정이 최적으로 재구성되었습니다.");
                    log.info("[Async] 식단 재구성 완료");
                })
                .exceptionally(throwable -> {
                    log.error("[Async] 식단 재구성 실패: ", throwable);
                    messagingTemplate.convertAndSend("/topic/meal/replan/" + userId,
                            "식단 재구성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
                    return null;
                });
    }

    @Async("mealTaskExecutor")
    @Override
    public CompletableFuture<Void> asyncRedistributeAfterMealTimeSkip(Long userId, LocalDate date, String skippedMealTime) {
        try {
            if (userId == null || date == null || skippedMealTime == null || skippedMealTime.isBlank()) {
                return CompletableFuture.completedFuture(null);
            }

            Meal.MealTime skippedMt;
            try {
                skippedMt = Meal.MealTime.valueOf(skippedMealTime.trim().toUpperCase());
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "끼니 정보를 이해하지 못했어요. (아침/점심/저녁)");
                return CompletableFuture.completedFuture(null);
            }

            List<Meal> dayMeals = mealSearch.findMealsByDateAndUser(userId, date);
            List<Meal> skippedNonAdditional = dayMeals.stream()
                    .filter(m -> m.getMealTime() == skippedMt)
                    .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                    .filter(m -> m.getStatus() == Meal.MealStatus.SKIPPED)
                    .toList();

            boolean hasNonAdditionalPlanned = dayMeals.stream()
                    .anyMatch(m -> m.getMealTime() == skippedMt
                            && (m.getIsAdditional() == null || !m.getIsAdditional())
                            && m.getStatus() == Meal.MealStatus.PLANNED);
            boolean hasNonAdditionalEaten = dayMeals.stream()
                    .anyMatch(m -> m.getMealTime() == skippedMt
                            && (m.getIsAdditional() == null || !m.getIsAdditional())
                            && m.getStatus() == Meal.MealStatus.EATEN);

            boolean isWholeMealTimeSkipped = !skippedNonAdditional.isEmpty() && !hasNonAdditionalPlanned && !hasNonAdditionalEaten;
            if (!isWholeMealTimeSkipped) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "해당 끼니가 '전체 생략' 상태가 아니라 재배분을 진행하지 않았어요.");
                return CompletableFuture.completedFuture(null);
            }

            MealTargetDto target = mealTargetService.getTargetByDate(userId, date);
            if (target == null) {
                try {
                    MealTargetDto created = ensureTargetAuto(userId, date);
                    if (created != null) target = created;
                } catch (Exception ignored) {
                    target = null;
                }
            }
            if (target == null || target.getGoalCal() == null || target.getGoalCal() <= 0) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "오늘 목표치가 없어 재정비를 진행할 수 없습니다.");
                return CompletableFuture.completedFuture(null);
            }

            int currentCal = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getCalories() != null ? m.getCalories() : 0)
                    .sum();
            int currentCarb = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getCarbs() != null ? m.getCarbs() : 0)
                    .sum();
            int currentProt = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getProtein() != null ? m.getProtein() : 0)
                    .sum();
            int currentFat = dayMeals.stream()
                    .filter(m -> m.getStatus() != Meal.MealStatus.SKIPPED)
                    .mapToInt(m -> m.getFat() != null ? m.getFat() : 0)
                    .sum();

            int gapCal = Math.max(0, (target.getGoalCal() != null ? target.getGoalCal() : 0) - currentCal);
            int gapCarb = Math.max(0, (target.getGoalCarbs() != null ? target.getGoalCarbs() : 0) - currentCarb);
            int gapProt = Math.max(0, (target.getGoalProtein() != null ? target.getGoalProtein() : 0) - currentProt);
            int gapFat = Math.max(0, (target.getGoalFat() != null ? target.getGoalFat() : 0) - currentFat);

            if (gapCal <= 0 && gapCarb <= 0 && gapProt <= 0 && gapFat <= 0) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "이미 하루 목표치에 근접해 있어 추가 메뉴를 만들지 않았어요.");
                return CompletableFuture.completedFuture(null);
            }

            Set<Meal.MealTime> remainingTimes = dayMeals.stream()
                    .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                    .filter(m -> m.getIsAdditional() == null || !m.getIsAdditional())
                    .map(Meal::getMealTime)
                    .filter(mt -> mt == Meal.MealTime.BREAKFAST || mt == Meal.MealTime.LUNCH || mt == Meal.MealTime.DINNER)
                    .filter(mt -> mt != skippedMt)
                    .collect(Collectors.toSet());

            List<Meal.MealTime> orderedTimes = List.of(Meal.MealTime.BREAKFAST, Meal.MealTime.LUNCH, Meal.MealTime.DINNER).stream()
                    .filter(remainingTimes::contains)
                    .toList();

            if (orderedTimes.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "남은 끼니가 없어 재배분을 진행할 수 없어요.");
                return CompletableFuture.completedFuture(null);
            }

            List<String> excludeKeywords = List.of("밥", "국", "찌개");
            Set<String> excludeFoodNames = skippedNonAdditional.stream()
                    .map(Meal::getFoodName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

            int n = orderedTimes.size();
            int baseCal = gapCal / n, remCal = gapCal % n;
            int baseCarb = gapCarb / n, remCarb = gapCarb % n;
            int baseProt = gapProt / n, remProt = gapProt % n;
            int baseFat = gapFat / n, remFat = gapFat % n;

            Map<Meal.MealTime, List<MealDto>> picksByTime = new java.util.LinkedHashMap<>();

            for (int idx = 0; idx < orderedTimes.size(); idx++) {
                Meal.MealTime mt = orderedTimes.get(idx);

                int tCal = baseCal + (idx < remCal ? 1 : 0);
                int tCarb = baseCarb + (idx < remCarb ? 1 : 0);
                int tProt = baseProt + (idx < remProt ? 1 : 0);
                int tFat = baseFat + (idx < remFat ? 1 : 0);

                AiMealRequestDto request = AiMealRequestDto.builder()
                        .requestType("PICK_FOODS")
                        .goal(AiMealRequestDto.GoalSpec.builder()
                                .targetCalories(Math.max(0, tCal))
                                .targetCarbs(Math.max(0, tCarb))
                                .targetProtein(Math.max(0, tProt))
                                .targetFat(Math.max(0, tFat))
                                .excludeKeywords(excludeKeywords)
                                .excludeFoodNames(new java.util.ArrayList<>(excludeFoodNames))
                                .minItems(1)
                                .maxItems(3)
                                .build())
                        .build();

                try {
                    var resp = aiMealClient.sendRequestAsync(request).join();
                    List<MealDto> suggested = resp != null ? resp.getSuggestedMeals() : null;
                    if (suggested == null) suggested = List.of();
                    List<MealDto> picked = suggested.stream()
                            .filter(m -> m != null && m.getFoodName() != null && !m.getFoodName().isBlank())
                            .limit(3)
                            .toList();
                    picksByTime.put(mt, new java.util.ArrayList<>(picked));
                    picked.stream()
                            .map(MealDto::getFoodName)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .forEach(excludeFoodNames::add);
                } catch (Exception e) {
                    log.warn("[Meal] PICK_FOODS 실패 - userId={}, date={}, mealTime={}, err={}", userId, date, mt, e.getMessage());
                    picksByTime.put(mt, List.of());
                }
            }

            int goalCal = target.getGoalCal() != null ? target.getGoalCal() : 0;
            if (goalCal > 0) {
                int maxAllowed = (int) Math.round(goalCal * 1.10);
                int addedCal = picksByTime.values().stream()
                        .flatMap(List::stream)
                        .mapToInt(m -> m != null && m.getCalories() != null ? m.getCalories() : 0)
                        .sum();
                int finalCal = currentCal + addedCal;

                if (finalCal > maxAllowed) {
                    class PickRef {
                        final Meal.MealTime mt;
                        final MealDto dto;
                        final int cal;
                        PickRef(Meal.MealTime mt, MealDto dto, int cal) { this.mt = mt; this.dto = dto; this.cal = cal; }
                    }

                    List<PickRef> all = new java.util.ArrayList<>();
                    for (var entry : picksByTime.entrySet()) {
                        for (MealDto m : entry.getValue()) {
                            int c = (m != null && m.getCalories() != null) ? m.getCalories() : 0;
                            all.add(new PickRef(entry.getKey(), m, c));
                        }
                    }
                    all.sort((a, b) -> Integer.compare(b.cal, a.cal));

                    for (PickRef ref : all) {
                        if (finalCal <= maxAllowed) break;
                        var list = picksByTime.get(ref.mt);
                        if (list == null || list.isEmpty()) continue;
                        boolean removed = list.remove(ref.dto);
                        if (!removed) {
                            for (int i = 0; i < list.size(); i++) {
                                MealDto x = list.get(i);
                                if (x == null) continue;
                                if (Objects.equals(x.getFoodName(), ref.dto.getFoodName())
                                        && Objects.equals(x.getCalories(), ref.dto.getCalories())) {
                                    list.remove(i);
                                    removed = true;
                                    break;
                                }
                            }
                        }
                        if (removed) {
                            finalCal -= ref.cal;
                        }
                    }
                }
            }

            boolean hasAnyPick = picksByTime.values().stream().anyMatch(list -> list != null && !list.isEmpty());
            if (!hasAnyPick) {
                messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "재배분할 추가 메뉴를 찾지 못했어요. 잠시 후 다시 시도해주세요.");
                return CompletableFuture.completedFuture(null);
            }

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                List<Meal> nowMeals = mealSearch.findMealsByDateAndUser(userId, date);

                List<Meal> toDelete = nowMeals.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsAdditional()))
                        .filter(m -> m.getStatus() == Meal.MealStatus.PLANNED)
                        .toList();
                if (!toDelete.isEmpty()) {
                    mealRepository.deleteAll(toDelete);
                    mealRepository.flush();
                }

                for (var entry : picksByTime.entrySet()) {
                    Meal.MealTime mt = entry.getKey();
                    List<MealDto> picked = entry.getValue() != null ? entry.getValue() : List.of();
                    for (MealDto p : picked) {
                        String foodName = p.getFoodName();
                        if (foodName == null || foodName.isBlank()) continue;
                        String serving = (p.getServingSize() == null || p.getServingSize().isBlank()) ? "1인분" : p.getServingSize();

                        MealDto dto = MealDto.builder()
                                .mealDate(date)
                                .mealTime(mt.name())
                                .status(Meal.MealStatus.PLANNED.name())
                                .isAdditional(true)
                                .foodName(foodName)
                                .servingSize(serving)
                                .calories(p.getCalories())
                                .carbs(p.getCarbs())
                                .protein(p.getProtein())
                                .fat(p.getFat())
                                .originalFoodName(p.getOriginalFoodName() != null ? p.getOriginalFoodName() : foodName)
                                .originalServingSize(p.getOriginalServingSize() != null ? p.getOriginalServingSize() : serving)
                                .originalCalories(p.getOriginalCalories() != null ? p.getOriginalCalories() : p.getCalories())
                                .originalCarbs(p.getOriginalCarbs() != null ? p.getOriginalCarbs() : p.getCarbs())
                                .originalProtein(p.getOriginalProtein() != null ? p.getOriginalProtein() : p.getProtein())
                                .originalFat(p.getOriginalFat() != null ? p.getOriginalFat() : p.getFat())
                                .build();

                        mealRepository.save(dto.toEntity(userId));
                    }
                }

                mealWsPublisher.publishMealChangedAfterCommit(userId);
            });

            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "하루 목표치를 기준으로 남은 끼니에 재정비(추가메뉴)했어요.");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("[Async] 끼니 생략 재배분 실패:", e);
            messagingTemplate.convertAndSend("/topic/meal/replan/" + userId, "재배분 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            return CompletableFuture.completedFuture(null);
        }
    }

    private Integer _ageFromBirthDate(String birthDate) {
        try {
            if (birthDate == null || birthDate.isBlank()) return null;
            LocalDate bd = LocalDate.parse(birthDate.trim());
            return Period.between(bd, LocalDate.now()).getYears();
        } catch (Exception e) {
            return null;
        }
    }
}


