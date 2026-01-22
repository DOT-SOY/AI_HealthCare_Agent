package com.backend.repository;

import com.backend.entity.Member;
import com.backend.entity.MemberBodyInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MemberBodyInfoRepository extends JpaRepository<MemberBodyInfo, Long> {
    
    // 회원별 신체 정보 조회
    List<MemberBodyInfo> findByMember(Member member);
    
    // 회원 ID로 신체 정보 조회
    @Query("SELECT mbi FROM MemberBodyInfo mbi WHERE mbi.member.id = :memberId")
    List<MemberBodyInfo> findByMemberId(@Param("memberId") String memberId);
    
    // 측정 시간 범위로 조회
    List<MemberBodyInfo> findByMeasuredTimeBetween(LocalDateTime start, LocalDateTime end);
    
    // 회원별 최신 신체 정보 조회
    List<MemberBodyInfo> findByMemberOrderByMeasuredTimeDesc(Member member);
}
