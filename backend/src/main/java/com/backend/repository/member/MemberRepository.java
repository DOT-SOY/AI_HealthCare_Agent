package com.backend.repository.member;

import com.backend.domain.member.Member;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.backend.domain.memberinfo.MemberInfoBody;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 관련 JPA Repository
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 이메일 중복 체크용
    Optional<Member> findByEmail(String email);

    // 이메일 사용 여부 (탈퇴 회원 제외) - existsById 패턴처럼 boolean 반환
    @Query("select count(m) > 0 from Member m where m.email = :email and m.isDeleted = false")
    boolean existsByEmailAndIsDeletedFalse(@Param("email") String email);

    // 시큐리티에서 권한 정보를 함께 가져올 때 사용 (탈퇴 회원 제외)
    @EntityGraph(attributePaths = {"roleList"})
    @Query("select m from Member m where m.email = :email and m.isDeleted = false")
    Member getWithRoles(@Param("email") String email);

    /**
     * 현재 회원과 동일한 성별/나이대(생년월일 범위) 이면서,
     * 주어진 운동 목적을 가진 신체 정보(MemberInfoBody)를 가진 활성 회원들을 한 번에 조회합니다.
     */
    @Query("""
           SELECT DISTINCT m
           FROM Member m
           INNER JOIN MemberInfoBody b ON m.id = b.memberId
           WHERE m.isDeleted = false
             AND b.deletedAt IS NULL
             AND b.exercisePurpose = :purpose
             AND m.gender = :gender
             AND m.birthDate BETWEEN :birthDateStart AND :birthDateEnd
           """)
    List<Member> findActiveGroupMembers(
            @Param("gender") Member.Gender gender,
            @Param("birthDateStart") LocalDate birthDateStart,
            @Param("birthDateEnd") LocalDate birthDateEnd,
            @Param("purpose") MemberInfoBody.ExercisePurpose purpose
    );
}