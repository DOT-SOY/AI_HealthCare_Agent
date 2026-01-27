package com.backend.controller;

import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.repository.member.MemberRepository;
import com.backend.security.token.RefreshCookieUtil;
import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.TokenType;
import com.backend.util.JWTUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class SocialController {

    private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    /**
     * 카카오 로그인
     * - 프론트에서 카카오 access token을 발급 받은 뒤, Authorization: Bearer {kakaoAccessToken}으로 호출
     * - 백엔드에서 카카오 사용자 정보 조회 → 회원 upsert → 우리 서비스 JWT/Refresh 발급
     */
    @GetMapping("/kakao")
    public ResponseEntity<?> kakaoLogin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MISSING_KAKAO_TOKEN",
                    "message", "카카오 access token이 필요합니다."
            ));
        }

        String kakaoAccessToken = authorization.substring(7);

        JsonObject kakaoUser;
        try {
            kakaoUser = fetchKakaoUser(kakaoAccessToken);
        } catch (Exception e) {
            log.warn("Kakao user fetch failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "KAKAO_AUTH_FAILED",
                    "message", "카카오 인증에 실패했습니다."
            ));
        }

        KakaoProfile profile = extractKakaoProfile(kakaoUser);
        if (profile.email == null || profile.email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "KAKAO_EMAIL_REQUIRED",
                    "message", "카카오 계정 이메일 제공 동의가 필요합니다. (카카오 로그인 동의항목에서 이메일 제공을 허용해주세요)"
            ));
        }

        // 회원 upsert
        Member member = memberRepository.findByEmail(profile.email).orElse(null);
        if (member != null && member.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DELETED_ACCOUNT",
                    "message", "탈퇴된 계정입니다."
            ));
        }

        if (member == null) {
            String randomPw = UUID.randomUUID().toString();
            member = Member.builder()
                    .email(profile.email)
                    .pw(passwordEncoder.encode(randomPw))
                    .name((profile.nickname == null || profile.nickname.isBlank()) ? profile.email : profile.nickname)
                    .gender(Member.Gender.MALE) // 기본값
                    .isDeleted(false)
                    .build();
            member.addRole(MemberRole.USER);
        } else {
            // 이름이 비어있으면 카카오 닉네임으로 보정
            if ((member.getName() == null || member.getName().isBlank()) && profile.nickname != null && !profile.nickname.isBlank()) {
                member.setName(profile.nickname);
            }
            // 성별이 null이면 기본값
            if (member.getGender() == null) {
                member.setGender(Member.Gender.MALE);
            }
            // 권한이 비어있으면 USER 부여
            if (member.getRoleList() == null || member.getRoleList().isEmpty()) {
                member.addRole(MemberRole.USER);
            }
        }

        memberRepository.save(member);

        // roles 포함해서 다시 조회 (탈퇴 제외 + roleList fetch)
        Member withRoles = memberRepository.getWithRoles(profile.email);
        if (withRoles == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "INTERNAL_ERROR",
                    "message", "회원 정보를 불러올 수 없습니다."
            ));
        }

        List<String> roleNames = withRoles.getRoleList().stream()
                .map(MemberRole::name)
                .collect(Collectors.toList());

        // claims 구성 (일반 로그인과 동일하게)
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", withRoles.getEmail());
        claims.put("name", withRoles.getName());
        claims.put("roleNames", roleNames);

        claims.put("tokenType", TokenType.ACCESS.name());
        claims.put("auth_time", Instant.now().getEpochSecond());
        claims.put("amr", "kakao");

        String accessToken = JWTUtil.generateToken(claims, 15);
        String refreshToken = refreshTokenService.issueNewSessionRefreshToken(withRoles.getEmail(), request, "kakao");
        RefreshCookieUtil.set(request, response, refreshToken, refreshTokenService.refreshCookieMaxAgeSeconds());

        claims.put("accessToken", accessToken);

        return ResponseEntity.ok(claims);
    }

    private JsonObject fetchKakaoUser(String kakaoAccessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(kakaoAccessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> res;
        try {
            res = restTemplate.exchange(KAKAO_USER_ME_URL, HttpMethod.GET, entity, String.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Kakao API call failed: " + e.getMessage(), e);
        }

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new IllegalStateException("Kakao API response invalid: " + res.getStatusCode());
        }

        return JsonParser.parseString(res.getBody()).getAsJsonObject();
    }

    private KakaoProfile extractKakaoProfile(JsonObject kakaoUser) {
        String email = null;
        String nickname = null;

        if (kakaoUser != null && kakaoUser.has("kakao_account") && kakaoUser.get("kakao_account").isJsonObject()) {
            JsonObject account = kakaoUser.getAsJsonObject("kakao_account");
            if (account.has("email") && !account.get("email").isJsonNull()) {
                email = account.get("email").getAsString();
            }

            if (account.has("profile") && account.get("profile").isJsonObject()) {
                JsonObject profile = account.getAsJsonObject("profile");
                if (profile.has("nickname") && !profile.get("nickname").isJsonNull()) {
                    nickname = profile.get("nickname").getAsString();
                }
            }
        }

        return new KakaoProfile(email, nickname);
    }

    private record KakaoProfile(String email, String nickname) {}
}

