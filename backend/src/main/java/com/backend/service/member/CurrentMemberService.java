package com.backend.service.member;

import com.backend.domain.member.Member;

import java.util.Optional;

/**
 * 현재 인증된 회원 조회 전용 서비스.
 * - Controller 에서 SecurityContextHolder 직접 접근을 금지하고,
 *   이 인터페이스를 통해서만 Member 조회/삭제여부 검증을 수행한다.
 */
public interface CurrentMemberService {

    /**
     * 현재 인증된 회원을 조회한다.
     *
     * @return 삭제되지 않은 Member 엔티티
     * @throws com.backend.common.exception.BusinessException
     *         - JWT_ERROR: 인증 정보가 없거나 유효하지 않은 경우
     *         - MEMBER_NOT_FOUND: 이메일로 회원을 찾을 수 없거나 삭제된 경우
     */
    Member getCurrentMemberOrThrow();

    /**
     * 현재 인증된 회원 ID를 조회한다. 미인증 시 빈 Optional 반환.
     */
    Optional<Long> getCurrentMemberIdOptional();
}

