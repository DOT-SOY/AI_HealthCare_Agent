package com.backend.client.meal;

import com.backend.client.BaseAIClient;
import com.backend.dto.meal.MealAiContextDto;
import com.backend.dto.meal.MealCommandResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * [Meal Command Client]
 * ai-server의 /api/meal/command를 호출하여 사용자의 자연어를 구조화된 작업 명령으로 변환합니다.
 *
 * 설계 이유:
 * - 기존의 키워드 파싱/정규식 중심 로직을 제거하고, "추론형" 엔진이 작업을 결정하게 함
 * - 백엔드에서는 DB 체크(겹침 여부 등) + 실제 저장/비동기 수행만 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MealCommandClient {

    private final BaseAIClient baseAIClient;

    public MealCommandResponseDto resolveCommand(String userText) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", userText);
        return baseAIClient.postRequest("/api/meal/command", body, MealCommandResponseDto.class);
    }

    public MealCommandResponseDto resolveCommand(String userText, MealAiContextDto context) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", userText);
        if (context != null) {
            body.put("context", context);
        }
        return baseAIClient.postRequest("/api/meal/command", body, MealCommandResponseDto.class);
    }
}







