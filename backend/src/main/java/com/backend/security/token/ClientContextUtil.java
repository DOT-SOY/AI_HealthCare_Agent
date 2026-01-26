package com.backend.security.token;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class ClientContextUtil {

    private ClientContextUtil() {}

    public static ClientContext from(HttpServletRequest request) {
        // User-Agent 처리 , User-Agent는 그대로 저장 x -> SHA-256 해시로 변환 후 저장
        String ua = request.getHeader("User-Agent");
        String uaHash = sha256Hex(ua == null ? "" : ua);

        // IP 추출
        String ip = extractClientIp(request);
        // IP 힌트로 정규화
        String ipHint = ip == null ? null : normalizeIpHint(ip);

        // ClientContext 생성, 다음 요청에서 비교용
        return ClientContext.builder()
                .userAgentHash(uaHash)
                .ipHint(ipHint)
                .build();
    }

    // 클라이언트 IP 추출 로직
    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // first IP is original client
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) return xrip.trim();
        return request.getRemoteAddr();
    }

    // IP 힌트 정규화
    private static String normalizeIpHint(String ip) {
        // Keep it coarse: /24 for IPv4, /64 for IPv6-like
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
            }
            return ip;
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            StringBuilder sb = new StringBuilder();
            int keep = Math.min(parts.length, 4); // rough /64-ish hint
            for (int i = 0; i < keep; i++) {
                if (i > 0) sb.append(":");
                sb.append(parts[i]);
            }
            sb.append("::/64");
            return sb.toString();
        }
        return ip;
    }

    // SHA-256 해시 함수, ( 문자열 → UTF-8 바이트 -> SHA-256 해시 -> 16진수 문자열 변환 )
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available");
        }
    }
}


