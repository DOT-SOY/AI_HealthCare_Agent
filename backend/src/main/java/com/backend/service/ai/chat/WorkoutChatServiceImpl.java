package com.backend.service.ai.chat;

import com.backend.client.RoutineRecommendClient;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.dto.response.RoutineRecommendResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.routine.RoutineService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WORKOUT 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutChatServiceImpl implements WorkoutChatService {

    private final RoutineService routineService;
    private final CurrentMemberService currentMemberService;
    private final GeneralChatService generalChatService;
    private final RoutineRecommendClient routineRecommendClient;

    @Override
    public AIChatResponse handleWorkout(IntentClassificationResult classification) {
        String action = classification.getAction();
        
        if (action == null) {
            log.warn("WORKOUT intent에서 action이 null입니다. 일반 채팅으로 처리");
            return generalChatService.handleGeneralChat(classification);
        }

        return switch (action.toUpperCase()) {
            case "QUERY" -> handleWorkoutQuery(classification);
            case "RECOMMEND" -> handleWorkoutRecommend(classification);
            case "MODIFY" -> handleWorkoutModify(classification);
            case "START" -> handleWorkoutStart(classification);
            default -> {
                log.info("WORKOUT intent에서 지원하지 않는 action: {}, 일반 채팅으로 처리", action);
                yield generalChatService.handleGeneralChat(classification);
            }
        };
    }

    /**
     * WORKOUT의 QUERY 액션 처리 (소분류: action)
     * 
     * - entities.date를 기준으로 해당 날짜의 루틴을 조회
     * - exercise_name, exercise_completed 필터링 지원
     * - RoutineResponse를 data에 담아서 프론트로 전달
     * - 루틴 데이터를 기반으로 자연스러운 메시지 생성
     */
    private AIChatResponse handleWorkoutQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;
        Object exerciseNameObj = entities != null ? entities.get("exercise_name") : null;
        Object exerciseCompletedObj = entities != null ? entities.get("exercise_completed") : null;

        LocalDate targetDate = AIChatUtils.resolveDate(dateObj);
        String exerciseName = exerciseNameObj != null ? exerciseNameObj.toString() : null;
        Boolean completed = parseExerciseCompleted(exerciseCompletedObj);

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse routine;
        
        if (exerciseName != null || completed != null) {
            routine = routineService.getRoutineByDateWithFilters(memberId, targetDate, exerciseName, completed);
        } else {
            routine = routineService.getRoutineByDate(memberId, targetDate);
        }

        String message;
        
        if (routine == null) {
            // 루틴이 없을 때: 풍부하고 친근한 메시지
            message = generateNoRoutineMessage(targetDate, exerciseName, completed);
        } else {
            // 루틴이 있을 때: 루틴 데이터를 기반으로 직접 자연스러운 메시지 생성
            message = generateRoutineBasedMessage(routine, targetDate);
        }

        return AIChatResponse.builder()
            .message(message)
            .intent("WORKOUT")
            .data(routine)
            .build();
    }
    
    /**
     * exercise_completed 엔티티를 Boolean으로 파싱합니다.
     */
    private Boolean parseExerciseCompleted(Object exerciseCompletedObj) {
        if (exerciseCompletedObj == null) {
            return null;
        }
        if (exerciseCompletedObj instanceof Boolean) {
            return (Boolean) exerciseCompletedObj;
        }
        if (exerciseCompletedObj instanceof String) {
            String str = ((String) exerciseCompletedObj).toLowerCase();
            // 완료 관련 키워드
            if (str.equals("true") || str.equals("완료") || str.equals("완료됨") || 
                str.equals("했어") || str.equals("했음") || str.equals("끝난") || 
                str.equals("끝났어") || str.equals("끝났음")) {
                return true;
            }
            // 미완료 관련 키워드
            if (str.equals("false") || str.equals("미완료") || str.equals("안했어") || 
                str.equals("안했음") || str.equals("남은") || str.equals("할") || 
                str.equals("해야할") || str.equals("해야 할")) {
                return false;
            }
        }
        return null;
    }

    /**
     * WORKOUT의 RECOMMEND 액션 처리 (소분류: action)
     *
     * - 대체 운동 요청(replace_exercise_name)이면: 해당 운동 대체만 반환
     * - 그 외: 2/4/5분할에 맞춰 전체 일차(2일/4일/5일) 추천을 모아 모달용 데이터 반환
     */
    private AIChatResponse handleWorkoutRecommend(IntentClassificationResult classification) {
        List<String> excludeBodyParts = resolveExcludeBodyParts(classification.getEntities());
        Integer splitType = resolveSplitType(classification.getEntities(), classification.getUserInput());
        List<String> targetBodyParts = resolveTargetBodyParts(classification.getEntities());
        String replaceExerciseName = resolveReplaceExerciseName(classification.getEntities());

        if (replaceExerciseName != null && !replaceExerciseName.isBlank()) {
            RoutineRecommendResponse recommendResponse = routineRecommendClient.recommend(
                excludeBodyParts, null, null, targetBodyParts, replaceExerciseName, null, null);
            String message = buildRecommendMessage(recommendResponse);
            Map<String, Object> data = new HashMap<>();
            data.put("exercises", recommendResponse.getExercises());
            if (recommendResponse.getAlternatives() != null) {
                data.put("alternatives", recommendResponse.getAlternatives());
            }
            return AIChatResponse.builder()
                .message(message)
                .intent("WORKOUT")
                .data(data)
                .build();
        }

        // 전체 분할 루틴: 2/4/5일차 각각 추천 후 모달용 데이터 구성 (일차당 4개, 일차 간 중복 제거)
        int daysCount = splitType != null && splitType >= 2 && splitType <= 5 ? splitType : 2;
        List<Map<String, Object>> daysPayload = new ArrayList<>();
        Map<String, Object> splitDefinitions = null;
        List<String> usedExerciseNames = new ArrayList<>();

        for (int dayIdx = 0; dayIdx < daysCount; dayIdx++) {
            RoutineRecommendResponse dayResponse = routineRecommendClient.recommend(
                excludeBodyParts, splitType, dayIdx, null, null, usedExerciseNames, 4);
            if (splitDefinitions == null && dayResponse.getSplitDefinitions() != null) {
                splitDefinitions = dayResponse.getSplitDefinitions();
            }
            List<Map<String, Object>> dayExercises = dayResponse.getExercises() != null ? dayResponse.getExercises() : List.of();
            for (Map<String, Object> ex : dayExercises) {
                Object nameObj = ex.get("name");
                if (nameObj != null && !String.valueOf(nameObj).isBlank()) {
                    usedExerciseNames.add(String.valueOf(nameObj).trim());
                }
            }
            String label = getDayLabel(splitDefinitions, splitType, dayIdx);
            Map<String, Object> dayPayload = new HashMap<>();
            dayPayload.put("dayIndex", dayIdx + 1);
            dayPayload.put("label", label);
            dayPayload.put("exercises", dayExercises);
            daysPayload.add(dayPayload);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("openRoutineRecommendModal", true);
        data.put("splitType", splitType != null ? splitType : 2);
        data.put("days", daysPayload);
        if (splitDefinitions != null) {
            data.put("splitDefinitions", splitDefinitions);
        }

        String message = splitType != null && splitType > 2
            ? splitType + "분할 루틴을 추천했어요. 확인해주세요."
            : "2분할 루틴을 추천했어요. 확인해주세요.";

        return AIChatResponse.builder()
            .message(message)
            .intent("WORKOUT")
            .data(data)
            .build();
    }

    /** 2/4/5 분할 영어 라벨 폴백 (AI 응답에 split_definitions 없을 때) */
    private static final Map<Integer, List<String>> SPLIT_LABELS_FALLBACK = Map.of(
        2, List.of("Upper day", "Leg day"),
        4, List.of("Chest & Triceps day", "Back & Biceps day", "Shoulder day", "Leg day"),
        5, List.of("Chest day", "Back day", "Shoulder day", "Arm day", "Leg day")
    );

    private String getDayLabel(Map<String, Object> splitDefinitions, Integer splitType, int dayIndex) {
        if (splitType != null && SPLIT_LABELS_FALLBACK.containsKey(splitType)) {
            List<String> fallback = SPLIT_LABELS_FALLBACK.get(splitType);
            if (dayIndex >= 0 && dayIndex < fallback.size()) {
                String fromFallback = fallback.get(dayIndex);
                if (splitDefinitions == null) {
                    return fromFallback;
                }
                String key = "split_" + splitType;
                Object listObj = splitDefinitions.get(key);
                if (!(listObj instanceof List)) {
                    return fromFallback;
                }
                List<?> list = (List<?>) listObj;
                if (dayIndex >= list.size()) {
                    return fromFallback;
                }
                Object item = list.get(dayIndex);
                if (item instanceof Map) {
                    Object name = ((Map<?, ?>) item).get("name");
                    if (name != null && !name.toString().isBlank()) {
                        String nameStr = name.toString().trim();
                        return nameStr.contains(" day") ? nameStr : nameStr + " day";
                    }
                }
                return fromFallback;
            }
        }
        if (splitDefinitions == null || splitType == null) {
            return dayIndex == 0 ? "Upper day" : (dayIndex == 1 ? "Leg day" : (dayIndex + 1) + "일차");
        }
        String key = "split_" + splitType;
        Object listObj = splitDefinitions.get(key);
        if (!(listObj instanceof List)) {
            return (dayIndex + 1) + "일차";
        }
        List<?> list = (List<?>) listObj;
        if (dayIndex >= list.size()) {
            return (dayIndex + 1) + "일차";
        }
        Object item = list.get(dayIndex);
        if (item instanceof Map) {
            Object name = ((Map<?, ?>) item).get("name");
            if (name != null && !name.toString().isBlank()) {
                String nameStr = name.toString().trim();
                return nameStr.contains(" day") ? nameStr : nameStr + " day";
            }
        }
        return (dayIndex + 1) + "일차";
    }

    private List<String> resolveExcludeBodyParts(Map<String, Object> entities) {
        if (entities == null) return Collections.emptyList();
        Object exclude = entities.get("exclude_body_parts");
        if (exclude instanceof List) {
            return ((List<?>) exclude).stream()
                .filter(e -> e != null)
                .map(String::valueOf)
                .collect(Collectors.toList());
        }
        Object painAreas = entities.get("pain_areas");
        if (painAreas instanceof List) {
            return ((List<?>) painAreas).stream()
                .filter(e -> e != null)
                .map(String::valueOf)
                .collect(Collectors.toList());
        }
        Object bodyPart = entities.get("body_part");
        if (bodyPart != null && !String.valueOf(bodyPart).isBlank()) {
            return List.of(String.valueOf(bodyPart).trim());
        }
        return Collections.emptyList();
    }

    private Integer resolveSplitType(Map<String, Object> entities, String userInput) {
        if (entities != null) {
            Object v = entities.get("split_type");
            if (v instanceof Number) return ((Number) v).intValue();
            if (v != null) {
                try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException ignored) { }
            }
        }
        if (userInput != null && !userInput.isBlank()) {
            String s = userInput.trim();
            if (s.contains("5분할") || s.contains("오분할") || s.contains("등가슴어깨팔하체")) return 5;
            if (s.contains("4분할") || s.contains("사분할") || s.contains("등가슴어깨하체")) return 4;
            if (s.contains("2분할") || s.contains("투분할") || s.contains("상체하체")) return 2;
        }
        return 2;
    }

    private List<String> resolveTargetBodyParts(Map<String, Object> entities) {
        if (entities == null) return null;
        Object v = entities.get("target_body_parts");
        if (v instanceof List) {
            return ((List<?>) v).stream()
                .filter(e -> e != null)
                .map(String::valueOf)
                .collect(Collectors.toList());
        }
        return null;
    }

    private String resolveReplaceExerciseName(Map<String, Object> entities) {
        if (entities == null) return null;
        Object v = entities.get("replace_exercise_name");
        if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v).trim();
        return null;
    }

    private String buildRecommendMessage(RoutineRecommendResponse res) {
        if (res == null || res.getMessage() == null) return "추천 결과를 불러오지 못했습니다.";
        StringBuilder sb = new StringBuilder();
        sb.append(res.getMessage());
        if (res.getExercises() != null && !res.getExercises().isEmpty()) {
            sb.append("\n\n추천 운동:\n");
            for (int i = 0; i < res.getExercises().size(); i++) {
                Map<String, Object> ex = res.getExercises().get(i);
                Object name = ex.get("exercise_name");
                Object bodyPart = ex.get("body_part");
                sb.append(i + 1).append(". ").append(name != null ? name : "운동");
                if (bodyPart != null) sb.append(" (").append(bodyPart).append(")");
                sb.append("\n");
            }
        }
        if (res.getAlternatives() != null && res.getAlternatives().get("alternatives") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> alts = (List<String>) res.getAlternatives().get("alternatives");
            if (alts != null && !alts.isEmpty()) {
                sb.append("\n대체 운동: ").append(String.join(", ", alts));
            }
        }
        return sb.toString();
    }

    /**
     * WORKOUT의 MODIFY 액션 처리
     * - swap_days: 두 날짜 루틴 맞바꾸기
     * - pain_modify: 통증 부위 배제한 대체 운동으로 해당 날 루틴 수정
     */
    private AIChatResponse handleWorkoutModify(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        if (entities == null) {
            return fallbackModifyMessage();
        }
        String modifyType = entities.get("modify_type") != null ? String.valueOf(entities.get("modify_type")).trim() : null;
        if ("add_exercise".equalsIgnoreCase(modifyType)) {
            return handleAddExercise(entities);
        }
        if ("remove_exercise".equalsIgnoreCase(modifyType)) {
            return handleRemoveExercise(entities);
        }
        if ("swap_days".equalsIgnoreCase(modifyType)) {
            return handleSwapDays(entities);
        }
        if ("pain_modify".equalsIgnoreCase(modifyType)) {
            return handlePainModify(entities);
        }
        return fallbackModifyMessage();
    }

    private AIChatResponse handleSwapDays(Map<String, Object> entities) {
        LocalDate date1 = AIChatUtils.resolveDateForSwap(entities.get("date1"));
        LocalDate date2 = AIChatUtils.resolveDateForSwap(entities.get("date2"));
        if (date1.equals(date2)) {
            return AIChatResponse.builder()
                .message("같은 날짜는 바꿀 수 없어요. 다른 두 날짜를 말씀해주세요.")
                .intent("WORKOUT")
                .build();
        }
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse r1 = routineService.getRoutineByDate(memberId, date1);
        RoutineResponse r2 = routineService.getRoutineByDate(memberId, date2);
        if (r1 == null || r2 == null) {
            return AIChatResponse.builder()
                .message("두 날짜 모두 루틴이 있어야 바꿀 수 있어요. " + AIChatUtils.formatDateForMessage(date1) + "·" + AIChatUtils.formatDateForMessage(date2) + " 중 루틴이 없는 날이 있어요.")
                .intent("WORKOUT")
                .build();
        }
        routineService.swapRoutineDays(memberId, date1, date2);
        String msg = String.format("%s와 %s 루틴을 바꿔두었어요.", AIChatUtils.formatDateForMessage(date1), AIChatUtils.formatDateForMessage(date2));
        return AIChatResponse.builder()
            .message(msg)
            .intent("WORKOUT")
            .data(java.util.Map.of("routineUpdated", true))
            .build();
    }

    /** 통증 부위를 RAG 부상위험부위와 매칭하기 위해 확장 (예: "다리" → 허벅지, 종아리, 무릎 등) */
    private static List<String> expandPainAreaForExclude(String painArea) {
        Set<String> set = new LinkedHashSet<>();
        set.add(painArea);
        switch (painArea) {
            case "다리":
                set.addAll(List.of("허벅지", "종아리", "무릎", "둔근", "햄스트링", "슬개건", "아킬레스건", "발목"));
                break;
            case "팔":
                set.addAll(List.of("손목", "팔꿈치", "어깨"));
                break;
            default:
                break;
        }
        return new ArrayList<>(set);
    }

    private AIChatResponse handlePainModify(Map<String, Object> entities) {
        String painArea = entities.get("pain_area") != null ? String.valueOf(entities.get("pain_area")).trim() : null;
        if (painArea == null || painArea.isEmpty()) {
            return AIChatResponse.builder()
                .message("어느 부위가 불편하신가요? (예: 허리, 어깨)")
                .intent("WORKOUT")
                .build();
        }
        LocalDate targetDate = AIChatUtils.resolveDate(entities.get("date"));
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse routine = routineService.getRoutineByDate(memberId, targetDate);
        if (routine == null || routine.getExercises() == null || routine.getExercises().isEmpty()) {
            return AIChatResponse.builder()
                .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴이 없거나 운동이 없어요. 먼저 루틴을 추가해주세요.")
                .intent("WORKOUT")
                .build();
        }
        List<String> excludeBodyParts = expandPainAreaForExclude(painArea);
        List<Map<String, Object>> replacements = new ArrayList<>();
        for (ExerciseResponse ex : routine.getExercises()) {
            String name = ex.getName();
            if (name == null || name.isBlank()) continue;
            RoutineRecommendResponse altResponse = routineRecommendClient.recommend(
                excludeBodyParts, null, null, null, name, null, 10);
            Map<String, Object> altMap = altResponse.getAlternatives();
            if (altMap != null) {
                Object hasRiskObj = altMap.get("has_risk_for_excluded_area");
                if (Boolean.FALSE.equals(hasRiskObj)) {
                    continue;
                }
            }
            List<String> alternatives = new ArrayList<>();
            if (altMap != null) {
                Object altsObj = altMap.get("alternatives");
                if (altsObj instanceof List) {
                    for (Object o : (List<?>) altsObj) {
                        if (o != null && !String.valueOf(o).isBlank()) {
                            alternatives.add(String.valueOf(o).trim());
                        }
                    }
                }
            }
            Map<String, Object> item = new HashMap<>();
            item.put("exerciseId", ex.getId());
            item.put("exerciseName", name);
            item.put("mainTarget", ex.getMainTarget());
            item.put("sets", ex.getSets());
            item.put("reps", ex.getReps());
            item.put("weight", ex.getWeight());
            item.put("alternatives", alternatives);
            replacements.add(item);
        }
        if (replacements.isEmpty()) {
            return AIChatResponse.builder()
                .message(String.format("%s 루틴을 확인했는데, %s에 부담을 주는 운동은 없었어요. 그대로 두시면 돼요.", AIChatUtils.formatDateForMessage(targetDate), painArea))
                .intent("WORKOUT")
                .build();
        }
        if (replacements.stream().allMatch(r -> ((List<?>) r.get("alternatives")).isEmpty())) {
            return AIChatResponse.builder()
                .message(String.format("%s 루틴을 확인했는데, %s에 부담을 주는 운동은 없었어요. 그대로 두시면 돼요.", AIChatUtils.formatDateForMessage(targetDate), painArea))
                .intent("WORKOUT")
                .build();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("openPainModifyModal", true);
        data.put("date", targetDate.toString());
        data.put("painArea", painArea);
        data.put("routineTitle", routine.getTitle());
        data.put("replacements", replacements);
        String message = String.format("%s에 부담이 적은 대체 운동을 골라주세요. 아래는 %s 부담이 적은 운동들이에요. 바꿀 것만 선택하면 돼요.", painArea, painArea);
        return AIChatResponse.builder()
            .message(message)
            .intent("WORKOUT")
            .data(data)
            .build();
    }

    /**
     * MODIFY add_exercise: 특정 날짜(기본 오늘)의 루틴에 운동을 추가합니다.
     */
    private AIChatResponse handleAddExercise(Map<String, Object> entities) {
        Object nameObj = entities.get("exercise_name");
        String exerciseName = nameObj != null ? String.valueOf(nameObj).trim() : null;
        if (exerciseName == null || exerciseName.isEmpty()) {
            return AIChatResponse.builder()
                .message("어떤 운동을 추가할까요? (예: 스쿼트 추가, 벤치프레스 넣어줘)")
                .intent("WORKOUT")
                .build();
        }

        LocalDate targetDate = AIChatUtils.resolveDate(entities.get("date"));
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();

        RoutineResponse routine = routineService.getRoutineByDate(memberId, targetDate);
        if (routine == null) {
            // 루틴이 없으면 우선 생성(비어있는 루틴) 후 운동 추가
            try {
                RoutineCreateRequest create = new RoutineCreateRequest(
                    targetDate,
                    AIChatUtils.formatDateForMessage(targetDate) + " 루틴",
                    null
                );
                routine = routineService.createRoutine(memberId, create);
            } catch (Exception ignored) {
                routine = routineService.getRoutineByDate(memberId, targetDate);
            }
        }
        if (routine == null) {
            return AIChatResponse.builder()
                .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴을 찾거나 만들지 못했어요. 먼저 루틴을 만들어주세요.")
                .intent("WORKOUT")
                .build();
        }

        try {
            // category는 null로 두면(이미 ExerciseType이 존재하면) DB의 ExerciseType을 사용합니다.
            routineService.addExercise(routine.getId(), new ExerciseAddRequest(exerciseName, null, 3, 10, null));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null && e.getMessage().contains("이미 같은 운동")
                ? "이미 " + AIChatUtils.formatDateForMessage(targetDate) + " 루틴에 '" + exerciseName + "' 운동이 있어요."
                : "운동 추가에 실패했어요: " + (e.getMessage() != null ? e.getMessage() : "알 수 없는 오류");
            return AIChatResponse.builder()
                .message(msg)
                .intent("WORKOUT")
                .data(Map.of("routineUpdated", false))
                .build();
        }

        return AIChatResponse.builder()
            .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴에 '" + exerciseName + "' 운동을 추가했어요.")
            .intent("WORKOUT")
            .data(Map.of("routineUpdated", true, "date", targetDate.toString(), "exerciseName", exerciseName))
            .build();
    }

    /**
     * MODIFY remove_exercise: 특정 날짜(기본 오늘)의 루틴에서 운동을 삭제합니다.
     */
    private AIChatResponse handleRemoveExercise(Map<String, Object> entities) {
        Object nameObj = entities.get("exercise_name");
        String exerciseName = nameObj != null ? String.valueOf(nameObj).trim() : null;
        if (exerciseName == null || exerciseName.isEmpty()) {
            return AIChatResponse.builder()
                .message("어떤 운동을 빼줄까요? (예: 스쿼트 빼줘, 벤치프레스 삭제해줘)")
                .intent("WORKOUT")
                .build();
        }

        LocalDate targetDate = AIChatUtils.resolveDate(entities.get("date"));
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse routine = routineService.getRoutineByDate(memberId, targetDate);

        if (routine == null) {
            return AIChatResponse.builder()
                .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴이 없어요.")
                .intent("WORKOUT")
                .build();
        }

        List<ExerciseResponse> exercises = routine.getExercises();
        if (exercises == null || exercises.isEmpty()) {
            return AIChatResponse.builder()
                .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴에 운동이 없어요.")
                .intent("WORKOUT")
                .build();
        }

        ExerciseResponse toRemove = exercises.stream()
            .filter(ex -> ex.getName() != null && exerciseName.equals(ex.getName().trim()))
            .findFirst()
            .orElse(null);

        if (toRemove == null) {
            return AIChatResponse.builder()
                .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴에 '" + exerciseName + "' 운동이 없어요.")
                .intent("WORKOUT")
                .build();
        }

        routineService.deleteExercise(memberId, routine.getId(), toRemove.getId());
        return AIChatResponse.builder()
            .message(AIChatUtils.formatDateForMessage(targetDate) + " 루틴에서 '" + exerciseName + "' 운동을 삭제했어요.")
            .intent("WORKOUT")
            .data(Map.of("routineUpdated", true, "date", targetDate.toString(), "exerciseName", exerciseName))
            .build();
    }

    private AIChatResponse fallbackModifyMessage() {
        return AIChatResponse.builder()
            .message("운동 추가는 \"스쿼트 추가해줘\"처럼, 운동 삭제는 \"스쿼트 빼줘\"처럼, 요일 맞바꾸기는 \"5일이랑 6일 바꿔줘\"처럼, 통증 수정은 \"허리 아파서 루틴 수정해줘\"처럼 말씀해주세요.")
            .intent("WORKOUT")
            .build();
    }

    /**
     * WORKOUT의 START 액션 처리 (소분류: action)
     * 
     * - 운동 시작 요청 처리
     * - exercise_name이 있으면 오늘 루틴에서 해당 운동 찾기
     * - 찾으면 모달 열기, 없으면 메시지 표시
     */
    private AIChatResponse handleWorkoutStart(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object exerciseNameObj = entities != null ? entities.get("exercise_name") : null;
        String exerciseName = exerciseNameObj != null ? exerciseNameObj.toString() : null;
        
        log.info("WORKOUT START 요청: exercise_name={}", exerciseName);
        
        // 운동명이 없으면 메시지만 표시
        if (exerciseName == null || exerciseName.trim().isEmpty()) {
            return AIChatResponse.builder()
                .message("어떤 운동을 시작하시겠어요? 운동명을 말씀해주세요. (예: 스쿼트 시작, 턱걸이 시작)")
                .intent("WORKOUT")
                .build();
        }
        
        // 오늘 루틴 조회
        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        RoutineResponse todayRoutine = routineService.getTodayRoutine(memberId);
        
        // 오늘 루틴이 없거나 운동이 없으면 메시지 표시
        if (todayRoutine == null || todayRoutine.getExercises() == null || todayRoutine.getExercises().isEmpty()) {
            return AIChatResponse.builder()
                .message("오늘 루틴에 " + exerciseName + " 운동이 없습니다. 먼저 루틴에 운동을 추가해주세요.")
                .intent("WORKOUT")
                .build();
        }
        
        // 오늘 루틴에서 해당 운동 찾기
        ExerciseResponse foundExercise = todayRoutine.getExercises().stream()
            .filter(ex -> ex.getName() != null && ex.getName().equals(exerciseName))
            .findFirst()
            .orElse(null);
        
        // 운동을 찾지 못하면 메시지 표시
        if (foundExercise == null) {
            return AIChatResponse.builder()
                .message("오늘 루틴에 " + exerciseName + " 운동이 없습니다. 먼저 루틴에 운동을 추가해주세요.")
                .intent("WORKOUT")
                .build();
        }
        
        // 운동을 찾았으면 모달 열기
        Map<String, Object> modalData = new HashMap<>();
        modalData.put("openExerciseModal", true);
        modalData.put("exerciseName", exerciseName);
        
        // 운동 정보에 routineId 추가
        Map<String, Object> exerciseData = new HashMap<>();
        exerciseData.put("id", foundExercise.getId());
        exerciseData.put("name", foundExercise.getName());
        exerciseData.put("mainTarget", foundExercise.getMainTarget());
        exerciseData.put("subTargets", foundExercise.getSubTargets());
        exerciseData.put("sets", foundExercise.getSets());
        exerciseData.put("reps", foundExercise.getReps());
        exerciseData.put("weight", foundExercise.getWeight());
        exerciseData.put("orderIndex", foundExercise.getOrderIndex());
        exerciseData.put("completed", foundExercise.isCompleted());
        exerciseData.put("routineId", todayRoutine.getId()); // routineId 추가
        
        modalData.put("exercise", exerciseData);
        
        return AIChatResponse.builder()
            .message("방금 운동 어땠는지 알려주세요.")
            .intent("WORKOUT")
            .data(modalData)
            .build();
    }

    /**
     * 루틴이 없을 때 풍부하고 친근한 메시지를 생성합니다.
     */
    private String generateNoRoutineMessage(LocalDate targetDate, String exerciseName, Boolean completed) {
        String dateStr = AIChatUtils.formatDateForMessage(targetDate);
        LocalDate today = LocalDate.now();
        
        StringBuilder sb = new StringBuilder();
        
        // 필터링 조건이 있는 경우
        if (exerciseName != null || completed != null) {
            sb.append(dateStr).append("에 ");
            if (exerciseName != null) {
                sb.append("'").append(exerciseName).append("' ");
            }
            if (completed != null) {
                sb.append(completed ? "완료된 " : "미완료인 ");
            }
            sb.append("운동 기록이 없습니다. ");
        } else {
            sb.append(dateStr).append("에는 운동 기록이 없습니다. ");
        }
        
        // 날짜에 따른 추가 메시지
        if (targetDate.equals(today)) {
            sb.append("오늘 운동 계획을 세우시거나 새로운 루틴을 시작해보시는 건 어떨까요? 💪");
        } else if (targetDate.isBefore(today)) {
            long daysAgo = ChronoUnit.DAYS.between(targetDate, today);
            if (daysAgo == 1) {
                sb.append("어제는 쉬는 날이셨나요? 오늘은 운동하시는 걸 추천드려요!");
            } else if (daysAgo <= 7) {
                sb.append("그때는 운동을 하지 않으셨네요. 꾸준한 운동이 중요하니 오늘부터 다시 시작해보세요!");
            } else {
                sb.append("그때는 운동 기록이 없었네요. 지금부터 꾸준히 운동하시면 좋은 결과가 있을 거예요!");
            }
        } else {
            sb.append("미래 날짜네요! 그날 운동 계획을 미리 세워보시는 것도 좋은 방법입니다.");
        }
        
        return sb.toString();
    }

    /**
     * 루틴 데이터를 기반으로 자연스러운 메시지를 생성합니다.
     * AI의 되묻기 형식 답변 대신, 실제 루틴 데이터를 바탕으로 직접 답변합니다.
     */
    private String generateRoutineBasedMessage(RoutineResponse routine, LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        String dateStr = AIChatUtils.formatDateForMessage(targetDate);
        
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            int totalExercises = routine.getExercises().size();
            long completedCount = routine.getExercises().stream()
                .filter(ex -> ex.isCompleted())
                .count();
            
            // 완료 상태에 따른 인사말
            if (completedCount == totalExercises) {
                sb.append(dateStr).append(" 운동 기록을 확인했어요! 모든 운동을 완료하셨네요! 🎉\n\n");
            } else if (completedCount > 0) {
                sb.append(dateStr).append(" 운동 기록을 확인했어요! ").append(completedCount).append("개 운동을 완료하셨고, ");
                sb.append(totalExercises - completedCount).append("개가 남아있어요.\n\n");
            } else {
                sb.append(dateStr).append(" 운동 계획을 확인했어요! 아직 시작하지 않은 루틴이네요. 화이팅! 💪\n\n");
            }
            
            // 상세 정보
            sb.append(formatRoutineMessage(routine, targetDate));
        } else {
            sb.append(dateStr).append(" 운동 기록을 확인했는데, 등록된 운동이 없네요.");
        }
        
        return sb.toString();
    }

    /**
     * 루틴 데이터를 자연어 메시지로 포맷팅합니다.
     */
    private String formatRoutineMessage(RoutineResponse routine, LocalDate targetDate) {
        StringBuilder sb = new StringBuilder();
        
        // 제목
        if (routine.getTitle() != null && !routine.getTitle().trim().isEmpty() && !routine.getTitle().equals("새로운 루틴")) {
            sb.append("📋 ").append(routine.getTitle()).append("\n\n");
        }

        // 운동 목록
        if (routine.getExercises() != null && !routine.getExercises().isEmpty()) {
            for (int i = 0; i < routine.getExercises().size(); i++) {
                var exercise = routine.getExercises().get(i);
                String exerciseName = exercise.getName() != null
                    ? exercise.getName()
                    : "알 수 없는 운동";
                
                // 완료 여부에 따른 이모지
                if (exercise.isCompleted()) {
                    sb.append("✅ ");
                } else {
                    sb.append("⏳ ");
                }
                
                sb.append(exerciseName);
                
                // 세트, 횟수, 무게 정보
                if (exercise.getSets() != null && exercise.getReps() != null) {
                    sb.append(" - ").append(exercise.getSets()).append("세트 × ");
                    sb.append(exercise.getReps()).append("회");
                    if (exercise.getWeight() != null && exercise.getWeight() > 0) {
                        sb.append(" (").append(exercise.getWeight()).append("kg)");
                    }
                }
                
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}

