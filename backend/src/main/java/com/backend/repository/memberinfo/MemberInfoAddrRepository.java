package com.backend.repository.memberinfo;

import com.backend.domain.memberinfo.MemberInfoAddr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberInfoAddrRepository extends JpaRepository<MemberInfoAddr, Long> {

    // 특정 회원의 배송지 목록 조회 (기본 배송지 우선, 최신순)
    @Query("SELECT m FROM MemberInfoAddr m WHERE m.memberId = :memberId ORDER BY m.isDefault DESC, m.createdAt DESC")
    List<MemberInfoAddr> findByMemberIdOrderByDefaultDesc(@Param("memberId") Long memberId);

    // 특정 회원의 기본 배송지 조회
    @Query("SELECT m FROM MemberInfoAddr m WHERE m.memberId = :memberId AND m.isDefault = true")
    Optional<MemberInfoAddr> findDefaultByMemberId(@Param("memberId") Long memberId);
}

