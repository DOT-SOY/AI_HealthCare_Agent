package com.backend.client;

import com.backend.dto.response.RoutineRecommendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python AI 서버의 /routine/recommend 엔드포인트 호출
 * RAG 기반 루틴/대체 운동 추천 (타겟 유지 + 위험 부위 배제)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoutineRecommendClient {

    private final BaseAIClient baseAIClient;

    /**
     * 루틴 추천 요청 (배제 부위만 지정 시 2분할 1일차 기준 추천)
     *
     * @param excludeBodyParts 부상 위험으로 제외할 부위 (예: 어깨, 허리)
     * @return 추천 결과
     */
    public RoutineRecommendResponse recommend(List<String> excludeBodyParts) {
        return recommend(excludeBodyParts, 2, 0, null, null, null, null);
    }

    /**
     * 루틴 추천 요청 (분할 + 요일 지정)
     *
     * @param excludeBodyParts 부상 위험으로 제외할 부위
     * @param splitType        분할 타입 (2, 4, 5)
     * @param dayIndex         요일 인덱스 (0-based)
     * @param targetBodyParts  타겟 부위 (선택)
     * @param replaceExerciseName 대체할 운동명 (선택)
     * @return 추천 결과
     */
    public RoutineRecommendResponse recommend(
            List<String> excludeBodyParts,
            Integer splitType,
            Integer dayIndex,
            List<String> targetBodyParts,
            String replaceExerciseName
    ) {
        return recommend(excludeBodyParts, splitType, dayIndex, targetBodyParts, replaceExerciseName, null, null);
    }

    /**
     * 루틴 추천 요청 (전체 파라미터: 이미 추천된 운동 제외, 일당 개수 제한)
     *
     * @param excludeExerciseNames 이미 추천된 운동명 목록 (중복 제거용)
     * @param limit                일당 추천 운동 수 (null이면 10)
     */
    public RoutineRecommendResponse recommend(
            List<String> excludeBodyParts,
            Integer splitType,
            Integer dayIndex,
            List<String> targetBodyParts,
            String replaceExerciseName,
            List<String> excludeExerciseNames,
            Integer limit
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        if (excludeBodyParts != null && !excludeBodyParts.isEmpty()) {
            requestBody.put("exclude_body_parts", excludeBodyParts);
        }
        if (splitType != null) {
            requestBody.put("split_type", splitType);
        }
        if (dayIndex != null) {
            requestBody.put("day_index", dayIndex);
        }
        if (targetBodyParts != null && !targetBodyParts.isEmpty()) {
            requestBody.put("target_body_parts", targetBodyParts);
        }
        if (replaceExerciseName != null && !replaceExerciseName.isBlank()) {
            requestBody.put("replace_exercise_name", replaceExerciseName);
        }
        if (excludeExerciseNames != null && !excludeExerciseNames.isEmpty()) {
            requestBody.put("exclude_exercise_names", excludeExerciseNames);
        }
        requestBody.put("limit", limit != null ? limit : 10);

        try {
            return baseAIClient.postRequest("/routine/recommend", requestBody, RoutineRecommendResponse.class);
        } catch (Exception e) {
            log.warn("루틴 추천 API 호출 실패, 스텁 응답 반환: {}", e.getMessage());
            return RoutineRecommendResponse.builder()
                    .message("운동 추천을 불러오는 중 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .exercises(new ArrayList<>())
                    .build();
        }
    }
}
