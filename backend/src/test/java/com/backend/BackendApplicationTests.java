package com.backend;

import com.backend.domain.Member;
import com.backend.domain.MemberRole;
import com.backend.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	/**
	 * USER 권한 회원, ADMIN 권한 회원을 각각 하나씩 생성하는 테스트
	 * - 회원가입/권한 매핑이 정상적으로 동작하는지 확인용
	 */
	@Test
	@Transactional
	void createUserAndAdminMembers() {
		// USER 권한 회원
		Member user = Member.builder()
				.email("user@example.com")
				.pw(passwordEncoder.encode("User1234!"))
				.name("일반유저")
				.gender(Member.Gender.MALE)
				.birthDate(LocalDate.of(1995, 1, 1))
				.build();
		user.addRole(MemberRole.USER);

		// ADMIN 권한 회원
		Member admin = Member.builder()
				.email("admin@example.com")
				.pw(passwordEncoder.encode("Admin1234!"))
				.name("관리자")
				.gender(Member.Gender.FEMALE)
				.birthDate(LocalDate.of(1990, 1, 1))
				.build();
		admin.addRole(MemberRole.ADMIN);

		memberRepository.save(user);
		memberRepository.save(admin);

		Member foundUser = memberRepository.findByEmail("user@example.com").orElseThrow();
		Member foundAdmin = memberRepository.findByEmail("admin@example.com").orElseThrow();

		assertThat(foundUser.getRoleList()).contains(MemberRole.USER);
		assertThat(foundAdmin.getRoleList()).contains(MemberRole.ADMIN);
	}
}
