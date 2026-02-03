package com.backend.service.ai;

import com.backend.dto.response.ConversationContext;
import com.backend.dto.response.IntentClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 대화 컨텍스트를 인메모리에 저장하고 관리하는 서비스 구현체
 * 사용자별로 최근 의도 분석 결과를 저장하며, TTL 20초로 자동 만료됩니다.
 */
@Service
@Slf4j
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final int TTL_SECONDS = 20; // TTL 20초
    private final Map<String, ConversationContext> contextMap = new ConcurrentHashMap<>(); // email을 키로 사용

    @Override
    public void saveContext(String email, IntentClassificationResult result) {
        if (email == null || email.trim().isEmpty() || result == null) {
            log.warn("컨텍스트 저장 실패: email={}, result={}", email, result);
            return;
        }

        ConversationContext context = new ConversationContext(result, LocalDateTime.now());
        // Map.put()은 키(email)가 이미 존재하면 기존 값을 덮어씁니다.
        // 따라서 사용자당 하나의 컨텍스트만 저장됩니다.
        contextMap.put(email, context);
        log.debug("컨텍스트 저장 완료: email={}, intent={}, action={}", 
            email, result.getIntent(), result.getAction());
    }

    @Override
    public IntentClassificationResult getContext(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        ConversationContext context = contextMap.get(email);
        if (context == null) {
            log.debug("컨텍스트 없음: email={}", email);
            return null;
        }

        // TTL 확인
        long secondsSinceCreation = ChronoUnit.SECONDS.between(context.getCreatedAt(), LocalDateTime.now());
        if (secondsSinceCreation >= TTL_SECONDS) {
            log.debug("컨텍스트 만료: email={}, 경과시간={}초", email, secondsSinceCreation);
            contextMap.remove(email);
            return null;
        }

        log.debug("컨텍스트 조회 성공: email={}, intent={}, action={}, 남은시간={}초", 
            email, context.getResult().getIntent(), context.getResult().getAction(), 
            TTL_SECONDS - secondsSinceCreation);
        return context.getResult();
    }
}

