package com.backend.service.member;

import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.repository.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminMemberCreateTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Admin 멤버 생성 성공")
    void createAdminMember_Success() {
        // given
        String email = "Admin";
        String rawPassword = "1111";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        Member adminMember = Member.builder()
                .email(email)
                .pw(encodedPassword)
                .name("관리자")
                .gender(Member.Gender.MALE)
                .birthDate(LocalDate.of(1990, 1, 1))
                .isDeleted(false)
                .build();

        // ADMIN 권한 추가
        adminMember.addRole(MemberRole.ADMIN);
        adminMember.addRole(MemberRole.USER); // 관리자는 USER 권한도 함께 가질 수 있음

        // when
        Member saved = memberRepository.save(adminMember);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("Admin");
        assertThat(saved.getName()).isEqualTo("관리자");
        assertThat(saved.getRoleList()).contains(MemberRole.ADMIN);
        assertThat(saved.getRoleList()).contains(MemberRole.USER);
        assertThat(saved.isDeleted()).isFalse();

        // 비밀번호 검증
        assertThat(passwordEncoder.matches(rawPassword, saved.getPw())).isTrue();

        System.out.println("✅ Admin 멤버 생성 완료!");
        System.out.println("   이메일: " + saved.getEmail());
        System.out.println("   비밀번호: 1111");
        System.out.println("   권한: " + saved.getRoleList());
    }
}

