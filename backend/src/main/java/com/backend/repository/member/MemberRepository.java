package com.backend.repository.member;

import com.backend.domain.member.Member;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 관련 JPA Repository
 * 현재 프로젝트에 맞게 최소 기능만 남겼습니다.
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
}