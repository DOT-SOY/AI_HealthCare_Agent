package com.backend.security.handler;

import com.backend.security.token.LoginLockService;
import com.backend.security.token.redis.LoginLockRedis;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class APILoginFailHandler implements AuthenticationFailureHandler {

    private final LoginLockService loginLockService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String email = request.getParameter("username");
        Map<String, Object> responseData = new HashMap<>();

        // BadCredentialsException 처리
        if (exception instanceof BadCredentialsException) {
            String exceptionMessage = exception.getMessage();

            // 잠금 상태 체크 (최우선)
            if (isAccountLocked(email, exceptionMessage)) {
                responseData = createLockedResponse(email, exceptionMessage);
            }
            // 특정 에러 코드 처리
            else if (exceptionMessage != null) {
                responseData = handleSpecificErrors(exceptionMessage, email);
            }
        }
        // 기타 예외 처리 (BadCredentialsException이 아닌 경우)
        else if (exception.getMessage() != null) {
            responseData = handleSpecificErrors(exception.getMessage(), email);
        }

        // 기본 에러 응답 (위에서 처리되지 않은 경우)
        if (responseData.isEmpty()) {
            // 마지막 안전장치: 잠금 상태 확인
            if (email != null && !email.isBlank() && loginLockService.isLocked(email)) {
                responseData = createLockedResponse(email, null);
            } else {
                responseData.put("error", "ERROR_LOGIN");
                responseData.put("message", "로그인에 실패했습니다.");
            }
        }

        sendResponse(response, responseData);
    }

    /**
     * 계정 잠금 여부 확인
     */
    private boolean isAccountLocked(String email, String exceptionMessage) {
        // 예외 메시지에서 잠금 확인
        boolean exceptionSaysLocked = (exceptionMessage != null &&
                (exceptionMessage.contains("로그인이 잠겨 있습니다") ||
                        exceptionMessage.contains("잠겨 있습니다") ||
                        exceptionMessage.contains("잠겨")));

        // Redis에서 잠금 상태 확인
        boolean redisLocked = (email != null && !email.isBlank() && loginLockService.isLocked(email));

        return exceptionSaysLocked || redisLocked;
    }

    /**
     * 잠금 응답 생성
     */
    private Map<String, Object> createLockedResponse(String email, String exceptionMessage) {
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("error", "ACCOUNT_LOCKED");

        int remainingMinutes = -1;

        // Redis에서 남은 시간 조회 (우선)
        if (email != null && !email.isBlank()) {
            remainingMinutes = loginLockService.getRemainingLockMinutes(email);
        }

        // Redis에서 못 가져왔으면 예외 메시지에서 추출
        if (remainingMinutes < 0 && exceptionMessage != null && exceptionMessage.contains("남은 시간:")) {
            remainingMinutes = extractMinutesFromMessage(exceptionMessage);
        }

        // 남은 시간이 있으면 포함, 없으면 기본 메시지
        if (remainingMinutes >= 0) {
            responseData.put("remainingMinutes", remainingMinutes);
            responseData.put("message", String.format("로그인이 잠겨 있습니다. 남은 시간: %d분", remainingMinutes));
        } else {
            responseData.put("message", exceptionMessage != null ? exceptionMessage : "로그인이 잠겨 있습니다.");
        }

        return responseData;
    }

    /**
     * 예외 메시지에서 남은 시간 추출
     */
    private int extractMinutesFromMessage(String message) {
        try {
            String[] parts = message.split("남은 시간:");
            if (parts.length > 1) {
                String minutesPart = parts[1].trim();
                String numberPart = minutesPart.replaceAll("[^0-9]", "");
                if (!numberPart.isEmpty()) {
                    return Integer.parseInt(numberPart);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    /**
     * 특정 에러 코드 처리
     */
    private Map<String, Object> handleSpecificErrors(String exceptionMessage, String email) {
        Map<String, Object> responseData = new HashMap<>();

        // 이미 잠긴 상태면 잠금 응답 반환
        if (email != null && !email.isBlank() && loginLockService.isLocked(email)) {
            return createLockedResponse(email, null);
        }

        // 특정 에러 코드 처리
        switch (exceptionMessage) {
            case "PENDING_APPROVAL":
                responseData.put("error", "PENDING_APPROVAL");
                responseData.put("message", "현재 승인 대기 상태입니다.");
                break;
            case "DELETED_ACCOUNT":
                responseData.put("error", "DELETED_ACCOUNT");
                responseData.put("message", "탈퇴된 계정입니다.");
                break;
            case "FACE_LOGIN_DISABLED":
                responseData.put("error", "FACE_LOGIN_DISABLED");
                responseData.put("message", "얼굴 로그인이 비활성화되어 있습니다.");
                break;
            case "FACE_NOT_RECOGNIZED":
                responseData.put("error", "FACE_NOT_RECOGNIZED");
                responseData.put("message", "등록된 얼굴을 찾을 수 없습니다.");
                break;
            case "Not Found":
            default:
                // 일반적인 로그인 실패 또는 기타 에러 - 실패 횟수 증가
                processLoginFailure(email, responseData);
                break;
        }

        return responseData;
    }

    /**
     * 로그인 실패 처리 (실패 횟수 증가 및 잠금 체크)
     */
    private void processLoginFailure(String email, Map<String, Object> responseData) {
        if (email == null || email.isBlank()) {
            responseData.put("error", "BAD_CREDENTIALS");
            responseData.put("message", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return;
        }

        LoginLockRedis lockInfo = loginLockService.getLockInfo(email);
        int failureCount = (lockInfo != null) ? lockInfo.getFailureCount() : 0;
        boolean locked = loginLockService.incrementFailureCount(email);

        if (locked) {
            // 계정 잠금됨
            int remainingMinutes = loginLockService.getRemainingLockMinutes(email);
            responseData.put("error", "ACCOUNT_LOCKED");
            responseData.put("remainingMinutes", remainingMinutes);
            responseData.put("message", String.format("로그인이 잠겨 있습니다. 남은 시간: %d분", remainingMinutes));
        } else {
            // 실패 횟수 정보 제공
            int currentFailures = failureCount + 1;
            int remainingAttempts = 5 - currentFailures;
            responseData.put("error", "BAD_CREDENTIALS");
            responseData.put("failureCount", currentFailures);
            responseData.put("remainingAttempts", remainingAttempts);
            responseData.put("message", String.format("로그인에 실패했습니다. 남은 시도 횟수: %d회", remainingAttempts));
        }
    }

    /**
     * HTTP 응답 전송
     */
    private void sendResponse(HttpServletResponse response, Map<String, Object> responseData) throws IOException {
        Gson gson = new Gson();
        String jsonStr = gson.toJson(responseData);

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = response.getWriter();
        printWriter.print(jsonStr);
        printWriter.flush();
        printWriter.close();
    }
}
