package com.backend.service.member;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * SecurityContext 기반 현재 회원 조회 구현체.
 * Controller 에서는 이 클래스를 통해서만 현재 로그인 회원을 조회한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentMemberServiceImpl implements CurrentMemberService {

    private final MemberRepository memberRepository;

    @Override
    public Member getCurrentMemberOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String)) {
            throw new BusinessException(ErrorCode.JWT_ERROR);
        }

        String email = (String) authentication.getPrincipal();

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email));

        if (member.isDeleted()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email);
        }

        return member;
    }
}

