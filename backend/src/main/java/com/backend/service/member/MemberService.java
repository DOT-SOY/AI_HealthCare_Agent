package com.backend.service.member;

import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.dto.member.MemberDTO;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

public interface MemberService {

    // 회원가입 기능
    Long join(MemberDTO memberDTO);

    // 회원 탈퇴 기능 (본인 탈퇴)
    void withdraw(String email);

    // [DTO -> Entity 변환] (회원가입 시 사용)
    // 비밀번호 암호화를 위해 PasswordEncoder를 파라미터로 받습니다.
    default Member dtoToEntity(MemberDTO dto, PasswordEncoder passwordEncoder) {
        Member member = Member.builder()
                .email(dto.getEmail())
                .pw(passwordEncoder.encode(dto.getPw())) // 암호화 적용 (비밀번호는 정제하지 않음 - 해싱으로 처리)
                .name(dto.getName())
                .gender(Member.Gender.valueOf(dto.getGender()))
                .birthDate(LocalDate.parse(dto.getBirthDate())) // String -> LocalDate 변환
                .build();

        // 기본 권한 부여: 모든 신규 회원에게 USER 역할 추가
        member.addRole(MemberRole.USER);

        return member;
    }
}