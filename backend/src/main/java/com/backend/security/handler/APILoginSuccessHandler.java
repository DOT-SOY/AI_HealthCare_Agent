package com.backend.security.handler;

import com.google.gson.Gson;
import com.backend.domain.Member;
import com.backend.domain.MemberRole;
import com.backend.repository.MemberRepository;
import com.backend.security.token.RefreshCookieUtil;
import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.LoginLockService;
import com.backend.security.token.TokenType;
import com.backend.util.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
public class APILoginSuccessHandler implements AuthenticationSuccessHandler {

    private final RefreshTokenService refreshTokenService;
    private final LoginLockService loginLockService;
    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        
        // CustomUserDetailsService에서 반환한 User 객체에서 이메일 추출
        User user = (User) authentication.getPrincipal();
        String email = user.getUsername();

        // DB에서 Member 엔티티 다시 조회 (JWT claims 구성에 필요한 정보 가져오기)
        Member member = memberRepository.getWithRoles(email);
        if (member == null) {
            log.error("로그인 성공했지만 Member 엔티티를 찾을 수 없음: {}", email);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"INTERNAL_ERROR\"}");
            return;
        }

        // JWT claims 구성
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", member.getEmail());
        claims.put("name", member.getName());
        
        // 권한 리스트를 문자열 리스트로 변환
        List<String> roleNames = member.getRoleList().stream()
                .map(MemberRole::name)
                .collect(Collectors.toList());
        claims.put("roleNames", roleNames);

        // Access Token 생성
        claims.put("tokenType", TokenType.ACCESS.name());
        claims.put("auth_time", Instant.now().getEpochSecond());
        String amr = (String) request.getAttribute("amr");
        claims.put("amr", amr == null ? "pwd" : amr);
        String accessToken = JWTUtil.generateToken(claims, 15);

        // 로그인 성공 시 실패 횟수 초기화
        loginLockService.resetFailureCount(email);

        // Refresh Token 발급 및 쿠키 설정
        String refreshToken = refreshTokenService.issueNewSessionRefreshToken(email, request, amr == null ? "pwd" : amr);
        RefreshCookieUtil.set(request, response, refreshToken, refreshTokenService.refreshCookieMaxAgeSeconds());

        // JSON 응답 (claims에 accessToken 추가)
        claims.put("accessToken", accessToken);

        Gson gson = new Gson();
        String jsonStr = gson.toJson(claims);

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = response.getWriter();
        printWriter.print(jsonStr);
        printWriter.flush();
        printWriter.close();
    }
}
