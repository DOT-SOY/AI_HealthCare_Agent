package com.backend.config.meal;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * [AI 서버 통신 설정]
 * FeignClient의 타임아웃, 재시도, 로그 수준을 상세 제어합니다.
 * 특히 Vision(대용량)과 LLM(긴 대기시간) 특성에 맞춰 타임아웃을 넉넉히 잡았습니다.
 */
@Configuration
public class AiMealConfig {

    /**
     * [타임아웃 설정]
     * - connectTimeout: 서버 연결까지 걸리는 시간 (5초)
     * - readTimeout: 연결 후 응답 받을 때까지 기다리는 시간 (60초)
     *   => Vision 분석이나 LLM 추론은 10~30초도 걸릴 수 있으므로 60초로 넉넉히 설정.
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                10000, TimeUnit.MILLISECONDS, // connectTimeout (10초)
                180000, TimeUnit.MILLISECONDS, // readTimeout read (3분)
                true
        );
    }

    /**
     * [재시도 전략]
     * AI 서버가 바빠서 튕기면 바로 포기하지 않고 3번까지 재시도합니다.
     * - 1초 간격으로 시작해서 최대 2초 간격까지 늘려가며 총 3회 시도.
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000, 2000, 3);
    }

    /**
     * [로그 레벨]
     * 통신 과정의 모든 헤더와 바디를 로그로 남겨서 디버깅을 돕습니다.
     * (실무 운영 시엔 BASIC이나 HEADERS로 낮추는 게 좋음)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}