package com.backend.security.token;

import com.backend.security.token.redis.LoginLockRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Log4j2
public class LoginLockService {

    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final int MAX_FAILURE_COUNT = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 로그인 실패 횟수 증가 및 잠금 처리
     * @param email 사용자 이메일
     * @return 잠금 여부 (true: 잠김, false: 아직 가능)
     */
    public boolean incrementFailureCount(String email) {
        String key = LOCK_KEY_PREFIX + email;
        LoginLockRedis lock = (LoginLockRedis) redisTemplate.opsForValue().get(key);

        if (lock == null) {
            // 첫 실패
            lock = LoginLockRedis.builder()
                    .userId(email)
                    .failureCount(1)
                    .locked(false)
                    .build();
        } else {
            // 기존 실패 기록 있음
            lock.setFailureCount(lock.getFailureCount() + 1);
        }

        // 5회 실패 시 잠금
        if (lock.getFailureCount() >= MAX_FAILURE_COUNT) {
            lock.setLocked(true);
            lock.setLockedAt(Instant.now().getEpochSecond());
            log.warn("Login locked for user: {} after {} failures", email, lock.getFailureCount());
        }

        Duration lockTtl = Duration.ofMinutes(LOCK_DURATION_MINUTES);
        redisTemplate.opsForValue().set(key, lock, lockTtl);

        return lock.isLocked();
    }

    /**
     * 로그인 성공 시 실패 횟수 초기화
     */
    public void resetFailureCount(String email) {
        String key = LOCK_KEY_PREFIX + email;
        redisTemplate.delete(key);
        log.debug("Login failure count reset for user: {}", email);
    }

    /**
     * 계정 잠금 여부 확인
     * @param email 사용자 이메일
     * @return 잠김 여부
     */
    public boolean isLocked(String email) {
        String key = LOCK_KEY_PREFIX + email;
        LoginLockRedis lock = (LoginLockRedis) redisTemplate.opsForValue().get(key);
        return lock != null && lock.isLocked();
    }

    /**
     * 잠금 상태 조회 및 남은 시간 계산 (분 단위)
     * @param email 사용자 이메일
     * @return 남은 잠금 시간 (분). 잠금되지 않았으면 -1
     */
    public int getRemainingLockMinutes(String email) {
        String key = LOCK_KEY_PREFIX + email;
        LoginLockRedis lock = (LoginLockRedis) redisTemplate.opsForValue().get(key);

        if (lock == null || !lock.isLocked()) {
            return -1;
        }

        // Redis TTL을 직접 조회
        Long ttlSeconds = redisTemplate.getExpire(key);

        if (ttlSeconds == null || ttlSeconds <= 0) {
            return 0; // 이미 만료됨
        }

        // 초를 분으로 변환 (올림 처리: 1초라도 남으면 1분으로 표시)
        return (int) Math.ceil(ttlSeconds / 60.0);
    }

    /**
     * 잠금 정보 전체 조회
     */
    public LoginLockRedis getLockInfo(String email) {
        String key = LOCK_KEY_PREFIX + email;
        return (LoginLockRedis) redisTemplate.opsForValue().get(key);
    }
}
