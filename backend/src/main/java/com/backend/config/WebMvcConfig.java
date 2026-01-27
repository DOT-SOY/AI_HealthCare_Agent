package com.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 * 
 * <p>정적 리소스 핸들러를 명시적으로 설정하여 API 경로(/api/**)가 정적 리소스로 처리되지 않도록 합니다.
 * Spring Boot 3.x에서 발생할 수 있는 "No static resource" 경고를 방지합니다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 리소스 핸들러를 명시적으로 설정
        // /api/** 경로는 제외하고, 기본 정적 리소스 경로만 처리
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/public/**")
                .addResourceLocations("classpath:/public/");
        registry.addResourceHandler("/resources/**")
                .addResourceLocations("classpath:/resources/");
    }
}
