package com.backend.security.filter;

import com.google.gson.Gson;
import com.backend.util.JWTUtil;
import com.backend.security.token.TokenType;
import com.backend.common.exception.JWTException;
import com.backend.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@Log4j2 // OncePerRequestFilter 상속 → 모든 HTTP 요청마다 한 번 실행되는 필터
public class JWTCheckFilter extends OncePerRequestFilter{
    // shouldNotFilter()가 false면 필터 로직(doFilterInternal)이 실행되어 JWT 검증 등 인증 처리가 수행되고,
    // true면 필터를 건너뛰고 다음 필터로 넘어간다.
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException{

        // Preflight(안전하지 않은)요청은 체크하지 않음
        if(request.getMethod().equals("OPTIONS")){
            return true;
        }

        String path = request.getRequestURI();

        log.info("check uri......................."+path);

        // “로그인 안 한 사용자도 접근 가능한 API”만 JWT 체크 안 함 (최소 예외)
        if (path.equals("/api/member/login") ||
                path.equals("/api/member/join") ||
                path.equals("/api/member/refresh") ||
                path.equals("/api/member/kakao") ||
                path.equals("/api/member/check-email")) {
            return true;
        }

        // 이미지 조회 경로는 체크하지 않음
        if (path.startsWith("/api/files/view/")) {
            return true;
        }

        // 파일 업로드 경로는 체크하지 않음 (TODO: 추후 ADMIN 권한으로 제한)
        if (path.equals("/api/files/upload")) {
            return true;
        }

        // 상품 관련 API는 모두 public 접근 허용 (TODO: 추후 ADMIN 권한으로 제한)
        if (path.equals("/api/v1/products") || path.startsWith("/api/v1/products/")) {
            return true;
        }

        return false;
    }
    @Override // 실제 JWT 검증 처리를 수행. 성공 → SecurityContext에 인증 정보 설정, 실패 → JSON 에러 응답
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException{

        log.info("------------------------JWTCheckFilter------------------");

        // WebSocket 핸드셰이크 요청인지 확인
        String upgradeHeader = request.getHeader("Upgrade");
        boolean isWebSocket = "websocket".equalsIgnoreCase(upgradeHeader) || request.getRequestURI().startsWith("/ws");

        // 클라이언트에서 Authorization: Bearer <JWT>로 전달
        String authHeaderStr = request.getHeader("Authorization");

        if (authHeaderStr == null || !authHeaderStr.startsWith("Bearer ")) {
            log.error("JWT Check Error: Authorization header is missing or invalid");

            // WebSocket 핸드셰이크 실패 시 403 Forbidden 반환
            if (isWebSocket) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().close();
                return;
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            PrintWriter printWriter = response.getWriter();
            printWriter.print(new Gson().toJson(Map.of("error", "UNAUTHORIZED")));
            printWriter.flush();
            printWriter.close();
            return;
        }

        try {
            //Bearer accestoken... "Bearer " 접두사 제거
            String accessToken = authHeaderStr.substring(7);
            // JWT 서명 확인 + payload(claims) 반환, 실패 시 예외 → catch 블록으로 이동
            Map<String, Object> claims = JWTUtil.validateToken(accessToken);

            // tokenType 구분(Refresh를 Access처럼 쓰는 우회 차단)
            Object tokenType = claims.get("tokenType");
            if (tokenType != null && !TokenType.ACCESS.name().equals(tokenType.toString())) {
                throw new JWTException(ErrorCode.JWT_INVALID_TOKEN_TYPE);
            }

            // 현재 MemberDTO는 회원가입용 DTO라 JWT의 상세 정보를 그대로 담지 않습니다.
            // 필터에서는 인증 여부만 확인하면 되므로, SecurityContext에는 email 문자열만 principal 로 저장합니다.
            String email = (String) claims.get("email");

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(email, null, null);

            // 민감행위 재확인(auth_time) 등에 사용할 수 있도록 최소 컨텍스트를 details로 부착
            authenticationToken.setDetails(Map.of(
                    "auth_time", claims.get("auth_time"),
                    "amr", claims.get("amr")
            ));

            // 지금 로그인한 사용자의 인증 정보(사용자 정보, 비밀번호, 권한 등)를 SecurityContext에 저장
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        }catch(JWTException e){ // 예외 처리 (JWT 검증 실패만 처리)
            log.error("JWT Check Error..............");
            log.error(e.getMessage());

            // WebSocket 핸드셰이크 실패 시
            if (isWebSocket) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().close();
                return;
            }

            Gson gson = new Gson();
            String msg = gson.toJson(Map.of("error", "ERROR_ACCESS_TOKEN"));

            response.setContentType("application/json; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            PrintWriter printWriter = response.getWriter();
            printWriter.print(msg);
            printWriter.flush();
            printWriter.close();
            return; // JWT 검증 실패 시 여기서 종료

        }catch(Exception e){ // JWT 검증과 무관한 예외는 그대로 전파
            // JWT 검증과 무관한 예외는 그대로 전파 (ServletException, IOException 등)
            throw e;
        }

        // JWT 검증 성공 시 필터 체인 계속 진행
        filterChain.doFilter(request, response);
    }

}