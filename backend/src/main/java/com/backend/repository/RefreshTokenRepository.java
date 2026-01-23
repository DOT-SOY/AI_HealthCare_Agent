package com.backend.repository;

import com.backend.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByRefreshTokenId(String refreshTokenId);

    List<RefreshToken> findByFamilyId(String familyId);

    /**
     * 사용자 이메일로 모든 Refresh Token 조회
     */
    List<RefreshToken> findByUserEmail(String userEmail);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.familyId = :familyId")
    void deleteByFamilyId(@Param("familyId") String familyId);

    /**
     * 사용자 이메일로 모든 Refresh Token 삭제
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userEmail = :userEmail")
    void deleteByUserEmail(@Param("userEmail") String userEmail);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") Instant now);
}

