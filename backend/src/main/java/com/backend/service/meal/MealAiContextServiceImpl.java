package com.backend.service.meal;

import com.backend.dto.meal.MealAiContextDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 기반 Meal AI 컨텍스트 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class MealAiContextServiceImpl implements MealAiContextService {

    private static final String KEY_PREFIX = "meal:ai:ctx:";
    // 정책(요구사항): 최근 3턴(왕복 3번) + TTL 30분
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_TURNS = 3; // 왕복 3턴
    private static final int MAX_MESSAGES = MAX_TURNS * 2; // user+assistant = 6

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis가 로컬 환경에서 미구동/불안정할 수 있어, 실패 시 인메모리로 degrade 합니다.
     * - 단일 인스턴스(dev)에서 멀티턴 UX가 깨지는 것을 방지
     * - Redis가 정상이라면 항상 Redis를 우선 사용
     */
    private final ConcurrentHashMap<Long, InMemoryEntry> inMemoryFallback = new ConcurrentHashMap<>();

    private static class InMemoryEntry {
        private final MealAiContextDto ctx;
        private final Instant expiresAt;

        private InMemoryEntry(MealAiContextDto ctx, Instant expiresAt) {
            this.ctx = ctx;
            this.expiresAt = expiresAt;
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public MealAiContextDto get(Long userId) {
        if (userId == null) return MealAiContextDto.builder().updatedAt(Instant.now()).build();
        try {
            String k = key(userId);
            Object obj = redisTemplate.opsForValue().get(k);
            if (obj instanceof MealAiContextDto dto) {
                return dto;
            }
            // 일부 환경에서는 GenericJackson2JsonRedisSerializer가 Map으로 역직렬화할 수 있어 방어합니다.
            if (obj instanceof Map<?, ?> m) {
                try {
                    MealAiContextDto dto = objectMapper.convertValue(m, MealAiContextDto.class);
                    if (dto != null) return dto;
                } catch (Exception ignored) {
                    // fall through
                }
            }
        } catch (Exception e) {
            log.warn("[MealAiContext] get failed: userId={}, err={}", userId, e.getMessage());
            MealAiContextDto fallback = getFromMemory(userId);
            if (fallback != null) return fallback;
        }
        MealAiContextDto fallback = getFromMemory(userId);
        if (fallback != null) return fallback;
        return MealAiContextDto.builder().updatedAt(Instant.now()).build();
    }

    @Override
    public MealAiContextDto appendUser(Long userId, String text) {
        return append(userId, "user", text);
    }

    @Override
    public MealAiContextDto appendAssistant(Long userId, String text) {
        return append(userId, "assistant", text);
    }

    @Override
    public MealAiContextDto setPending(Long userId, String type, Map<String, Object> data) {
        MealAiContextDto ctx = get(userId);
        ctx.setPending(MealAiContextDto.Pending.builder()
                .type(type)
                .data(data != null ? data : java.util.Map.of())
                .at(Instant.now())
                .build());
        ctx.setUpdatedAt(Instant.now());
        save(userId, ctx);
        return ctx;
    }

    @Override
    public MealAiContextDto clearPending(Long userId) {
        MealAiContextDto ctx = get(userId);
        ctx.setPending(null);
        ctx.setUpdatedAt(Instant.now());
        save(userId, ctx);
        return ctx;
    }

    @Override
    public void reset(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception e) {
            log.warn("[MealAiContext] reset failed: userId={}, err={}", userId, e.getMessage());
        } finally {
            inMemoryFallback.remove(userId);
        }
    }

    private MealAiContextDto append(Long userId, String role, String text) {
        MealAiContextDto ctx = get(userId);
        List<MealAiContextDto.Message> history = ctx.getHistory();
        if (history == null) history = new ArrayList<>();

        String content = text == null ? "" : text.trim();
        if (!content.isBlank()) {
            history.add(MealAiContextDto.Message.builder()
                    .role(role)
                    .content(content)
                    .at(Instant.now())
                    .build());
        }

        // keep only last MAX_MESSAGES
        if (history.size() > MAX_MESSAGES) {
            history = history.subList(Math.max(0, history.size() - MAX_MESSAGES), history.size());
        }

        ctx.setHistory(new ArrayList<>(history));
        ctx.setUpdatedAt(Instant.now());
        save(userId, ctx);
        return ctx;
    }

    private void save(Long userId, MealAiContextDto ctx) {
        if (userId == null) return;
        try {
            String k = key(userId);
            redisTemplate.opsForValue().set(k, ctx, TTL);
        } catch (Exception e) {
            log.warn("[MealAiContext] save failed: userId={}, err={}", userId, e.getMessage());
            saveToMemory(userId, ctx);
        }
    }

    private MealAiContextDto getFromMemory(Long userId) {
        if (userId == null) return null;
        try {
            InMemoryEntry entry = inMemoryFallback.get(userId);
            if (entry == null) return null;
            if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) {
                inMemoryFallback.remove(userId);
                return null;
            }
            return entry.ctx;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveToMemory(Long userId, MealAiContextDto ctx) {
        if (userId == null) return;
        try {
            Instant exp = Instant.now().plus(TTL);
            inMemoryFallback.put(userId, new InMemoryEntry(ctx, exp));
        } catch (Exception ignored) {
            // ignore
        }
    }
}



