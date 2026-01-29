package com.backend.security.token.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Redis에 저장되는 로그인 잠금 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLockRedis implements Serializable {
    private String userId; // email
    private int failureCount; // 실패 횟수
    private boolean locked; // 잠금 여부
    private long lockedAt; // 잠금 시작 시간 (epoch seconds)
}
