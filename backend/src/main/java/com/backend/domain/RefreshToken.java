package com.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_jti", columnList = "refresh_token_id"),
        @Index(name = "idx_refresh_token_family", columnList = "family_id"),
        @Index(name = "idx_refresh_token_email", columnList = "user_email")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refresh_token_id", nullable = false, unique = true, length = 64)
    private String refreshTokenId; // jti

    @Column(name = "refresh_token", nullable = false, length = 500)
    private String refreshToken; // JWT 토큰 자체 (무결성 검증용)

    @Column(name = "user_email", nullable = false, length = 100)
    private String userEmail;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false; // 사용 여부 (재발급 시 true로 변경)

    @Column(name = "user_agent_hash", nullable = false, length = 64)
    private String userAgentHash;

    @Column(name = "ip_address", length = 50)
    private String ipAddress; // IP 힌트 (예: /24, /64 prefix)

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public void markAsUsed() {
        this.used = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

