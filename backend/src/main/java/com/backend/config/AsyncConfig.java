package com.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * [비동기 처리 설정]
 * Meal 관련 AI 분석 작업을 위한 전용 ThreadPool 설정
 * 
 * 이유:
 * - 기본 스레드 풀 대신 커스텀 ThreadPool 사용으로 성능 최적화
 * - 리소스 관리 및 모니터링 용이
 * - meal 전용 스레드 풀로 다른 작업과 격리
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Meal AI 분석 작업 전용 ThreadPool
     * 
     * 설정 이유:
     * - corePoolSize: 5 - 기본 유지 스레드 수 (AI 분석 작업 평균 처리 시간 고려)
     * - maxPoolSize: 10 - 최대 스레드 수 (동시 요청 처리)
     * - queueCapacity: 100 - 대기 큐 크기 (과부하 방지)
     * - threadNamePrefix: "meal-async-" - 로깅 및 모니터링 용이
     */
    @Bean(name = "mealTaskExecutor")
    public Executor mealTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("meal-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}


