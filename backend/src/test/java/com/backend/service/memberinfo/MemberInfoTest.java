package com.backend.service.memberinfo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.domain.member.Member;
import com.backend.repository.member.MemberRepository;
import org.springframework.data.domain.Sort;

import java.util.UUID;

/**
 * member_info_body 테이블 구조를 엔티티(MemberInfoBody + BaseEntity)에 맞게
 * 다시 만들고, 샘플 데이터를 넣어보는 테스트용 클래스입니다.
 *
 * 실제 서비스 로직은 건드리지 않고, DB 스키마/데이터만 맞추는 용도입니다.
 */
@SpringBootTest
@SuppressWarnings("null")
public class MemberInfoTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberRepository memberRepository;

    private Long resolveMemberId() {
        return memberRepository.findAll(Sort.by("id")).stream()
                .filter(member -> !member.isDeleted())
                .findFirst()
                .map(Member::getId)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email("dummy-" + UUID.randomUUID() + "@example.com")
                        .pw("pw")
                        .name("dummy-user")
                        .gender(Member.Gender.MALE)
                        .build()).getId());
    }

    @Test
       public void resetMemberInfoBodyTableAndInsertSample() {
        Long memberId = resolveMemberId();

        // 1. 엔티티 구조에 맞는 테이블 생성 (없을 때만)
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS member_info_body (
                    body_info_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id BIGINT NOT NULL,
                    height_cm DOUBLE,
                    weight_kg DOUBLE,
                    skeletal_muscle_mass DOUBLE,
                    body_fat_percent DOUBLE,
                    body_water DOUBLE,
                    protein DOUBLE,
                    minerals DOUBLE,
                    body_fat_mass DOUBLE,
                    target_weight DOUBLE,
                    weight_control DOUBLE,
                    fat_control DOUBLE,
                    muscle_control DOUBLE,
                    goal_type VARCHAR(20), -- DIET, MAINTAIN, BULK_UP
                    measured_time DATETIME,
                    deleted_at TIMESTAMP NULL, -- BaseEntity.deletedAt
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
                """);

        // 2. 기존 더미 데이터 제거 (같은 멤버에 중복 삽입 방지)
        jdbcTemplate.update("DELETE FROM member_info_body WHERE member_id = ?", memberId);

        // 3. 샘플 데이터 삽입
        String insertSql = """
                INSERT INTO member_info_body
                (
                  member_id, measured_time, height_cm, weight_kg,
                  skeletal_muscle_mass, body_fat_percent,
                  body_water, protein, minerals, body_fat_mass,
                  target_weight, weight_control, fat_control, muscle_control,
                  goal_type
                )
                VALUES
                (%d, '2025-12-03 09:00:00', 175.0, 72.4, 31.8, 23.5, 40.2, 11.8, 3.9, 17.0, 68.0, -4.4, -3.2, 1.0, 'DIET'),
                (%d, '2025-12-10 09:00:00', 175.0, 72.0, 31.9, 23.1, 40.3, 11.9, 3.9, 16.6, 68.0, -4.0, -3.0, 1.0, 'DIET'),
                (%d, '2025-12-17 09:00:00', 175.0, 71.6, 32.0, 22.8, 40.4, 11.9, 3.9, 16.3, 68.0, -3.6, -2.8, 1.0, 'DIET'),
                (%d, '2025-12-24 09:00:00', 175.0, 71.2, 32.1, 22.5, 40.5, 12.0, 3.9, 16.0, 68.0, -3.2, -2.6, 1.0, 'DIET'),
                (%d, '2025-12-31 09:00:00', 175.0, 70.8, 32.2, 22.1, 40.6, 12.0, 3.9, 15.7, 68.0, -2.8, -2.4, 1.0, 'DIET'),
                (%d, '2026-01-07 09:00:00', 175.0, 70.5, 32.3, 21.8, 40.7, 12.1, 4.0, 15.4, 68.0, -2.5, -2.2, 1.0, 'DIET'),
                (%d, '2026-01-14 09:00:00', 175.0, 70.2, 32.4, 21.5, 40.8, 12.1, 4.0, 15.1, 68.0, -2.2, -2.0, 1.0, 'DIET'),
                (%d, '2026-01-21 09:00:00', 175.0, 69.9, 32.5, 21.2, 40.9, 12.2, 4.0, 14.8, 68.0, -1.9, -1.8, 1.0, 'DIET');
                """.formatted(memberId, memberId, memberId, memberId, memberId, memberId, memberId, memberId);
        jdbcTemplate.execute(insertSql);

        // 4. 간단 검증용 조회 (필요하면 로그로 찍어보기)
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_info_body WHERE member_id = ?",
                Long.class,
                memberId
        );
        System.out.println("member_id=" + memberId + " row count = " + count);
    }
}