package com.backend.security.handler;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class CustomAccessDeniedHandler implements AccessDeniedHandler{

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        Gson gson = new Gson();

        // Gson 라이브러리를 사용해 Map → JSON 변환
        String jsonStr = gson.toJson(Map.of("error", "ERROR_ACCESSDENIED"));

        // Content-Type → JSON with UTF-8 charset
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // HTTP 상태 코드 → 403 (권한 없음)
        response.setStatus(HttpStatus.FORBIDDEN.value());
        // JSON 문자열을 클라이언트에 전달
        PrintWriter printWriter = response.getWriter();
        printWriter.print(jsonStr);
        printWriter.flush();
        printWriter.close();

    }

}