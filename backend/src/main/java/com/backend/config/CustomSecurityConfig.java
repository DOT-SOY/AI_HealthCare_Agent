package com.backend.config;

import com.backend.repository.member.MemberRepository;
import com.backend.security.CustomUserDetailsService;
import com.backend.security.filter.JWTCheckFilter;
import com.backend.security.handler.APILoginFailHandler;
import com.backend.security.handler.APILoginSuccessHandler;
import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.LoginLockService;
import com.backend.security.handler.CustomAccessDeniedHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@Log4j2
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class CustomSecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    // application.properties의 refresh.token.storage 설정에 따라 자동으로 Redis 또는 DB가 주입됨
    private final RefreshTokenService refreshTokenService;
    private final LoginLockService loginLockService;
    private final MemberRepository memberRepository;

    @Bean // 비밀번호 암호화(BCrypt 해시 방식으로 암호화), (@Bean)스프링 전역에서 사용가능
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        log.info("---------------------security config---------------------------");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json; charset=UTF-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
                        })
                );
        // 접근 권한 예외 처리
        http.exceptionHandling(config -> {config.accessDeniedHandler(new CustomAccessDeniedHandler());
        });

        http.authenticationProvider(authenticationProvider(passwordEncoder()));

        // Spring Security의 CORS 필터를 활성화하고 corsConfigurationSource() 설정에 따라 프론트엔드의 API 요청을 허용한다.
        http.cors(httpSecurityCorsConfigurer -> {
            httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource());
        });

        /*
         * 공개/보호 정책의 유일한 정의처. 신규 API 추가·권한 변경 시 이 블록만 수정한다.
         * JWTCheckFilter는 "토큰이 있으면 검증해서 인증 세팅"만 담당하며, 경로별 예외를 두지 않는다.
         */
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                /* 공개: 회원 로그인·가입·리프레시·소셜·이메일체크 등 (추후 member 일부만 공개로 좁힐 수 있음) */
                .requestMatchers("/api/member/**").permitAll()
                /* 공개: 파일 조회 */
                .requestMatchers("/api/files/view/**").permitAll()
                /* 공개(예정: ADMIN 전용으로 조정 가능) */
                .requestMatchers("/api/files/upload").permitAll()
                /* 상품: 조회만 공개, 등록/수정/삭제는 ADMIN */
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                /* 카트: 게스트·로그인 모두 허용 */
                .requestMatchers("/api/cart/**").permitAll()
                /* 그 외: 인증 필수 */
                .anyRequest().authenticated()
        );

        // 로그인 설정 (JWT 발급 지점)
        http.formLogin(config ->{
            config.loginPage("/api/member/login");
            config.successHandler(apiLoginSuccessHandler());
            config.failureHandler(new APILoginFailHandler(loginLockService));
        });

        // JWT 필터: 토큰이 있으면 검증 후 인증만 세팅. 공개/보호 판단은 authorizeHttpRequests에서만 수행.
        http.addFilterBefore(new JWTCheckFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public APILoginSuccessHandler apiLoginSuccessHandler() {
        return new APILoginSuccessHandler(refreshTokenService, loginLockService, memberRepository);
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // 모든 도메인 허용
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        // REST API 전용
        // ✅ OPTIONS(Preflight) 포함 (브라우저가 Authorization 헤더 사용 시 OPTIONS를 먼저 보냄)
        configuration.setAllowedMethods(Arrays.asList("OPTIONS", "HEAD", "GET", "POST", "PUT", "PATCH", "DELETE"));
        // JWT 전달을 위한 Authorization 헤더 허용
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        // 다운로드 시 파일명, 카트 guest_token 발급 시 Set-Cookie 노출
        configuration.setExposedHeaders(Arrays.asList("Content-Disposition", "Set-Cookie"));
        // 쿠키/인증 정보 허용
        configuration.setAllowCredentials(true);

        // URL별로 CORS 정책 적용 가능 (모든 URL에 위 정책 적용)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);

        return authProvider;
    }

}