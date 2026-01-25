package com.backend.security;

import com.backend.domain.member.Member;
import com.backend.repository.member.MemberRepository;
import com.backend.security.token.LoginLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Log4j2
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final LoginLockService loginLockService;

    @Override
    public UserDetails loadUserByUsername(String username) throws BadCredentialsException {
        log.info("loadUserByUsername: {}", username);

        // 로그인 잠금 체크
        if (loginLockService.isLocked(username)) {
            int remainingMinutes = loginLockService.getRemainingLockMinutes(username);
            String message = String.format("로그인이 잠겨 있습니다. 남은 시간: %d분", remainingMinutes);
            throw new BadCredentialsException(message);
        }

        Member member = memberRepository.getWithRoles(username);

        if (member == null) {
            throw new BadCredentialsException("Not Found");
        }

        // 탈퇴한 회원은 로그인 불가 (getWithRoles 쿼리에서 이미 제외되지만, 이중 체크)
        if (member.isDeleted()) {
            throw new BadCredentialsException("탈퇴한 회원입니다.");
        }

        // 권한 리스트를 Spring Security용 Authority로 변환
        List<GrantedAuthority> authorities = member.getRoleList().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        // DB에서 가져온 회원 정보를 Spring Security 기본 UserDetails 구현체로 변환
        // 현재 MemberDTO는 회원가입용 DTO라 여기서는 사용하지 않고,
        // 스프링이 제공하는 User 클래스를 사용해서 인증 객체를 생성합니다.
        return new User(member.getEmail(), member.getPw(), authorities);
    }
}
