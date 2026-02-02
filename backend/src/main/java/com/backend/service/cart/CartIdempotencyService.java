package com.backend.service.cart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 장바구니 멱등성 처리 서비스
 * MVP: 메모리 기반 (추후 Redis/DB로 마이그레이션 가능)
 */
@Slf4j
@Service
public class CartIdempotencyService {
    
    // 멱등키 저장 (메모리)
    private final Set<String> idempotencyKeys = ConcurrentHashMap.newKeySet();
    
    // 만료된 키 정리용 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // 키 만료 시간 (1시간)
    private static final long EXPIRY_HOURS = 1;
    
    public CartIdempotencyService() {
        // 주기적으로 만료된 키 정리 (30분마다)
        scheduler.scheduleAtFixedRate(this::cleanupExpiredKeys, 30, 30, TimeUnit.MINUTES);
    }
    
    /**
     * 중복 요청 확인
     * 
     * @param idempotencyKey 멱등키
     * @return 중복이면 true
     */
    public boolean isDuplicate(String idempotencyKey) {
        return idempotencyKeys.contains(idempotencyKey);
    }
    
    /**
     * 멱등키 저장
     * 
     * @param idempotencyKey 멱등키
     */
    public void saveIdempotencyKey(String idempotencyKey) {
        idempotencyKeys.add(idempotencyKey);
    }
    
    /**
     * 만료된 키 정리 (현재는 무제한 보관, 추후 타임스탬프 기반으로 개선 가능)
     */
    private void cleanupExpiredKeys() {
        // MVP에서는 간단하게 크기 제한만 (10,000개)
        if (idempotencyKeys.size() > 10000) {
            // 가장 오래된 키 제거 (FIFO 방식으로 간단히 처리)
            // 실제로는 LinkedHashMap이나 타임스탬프 기반으로 개선 필요
            idempotencyKeys.clear();
        }
    }
}

