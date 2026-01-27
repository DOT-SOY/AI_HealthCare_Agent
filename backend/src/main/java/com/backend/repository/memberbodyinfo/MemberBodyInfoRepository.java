package com.backend.repository.memberbodyinfo;

import com.backend.domain.member.Member;
import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MemberBodyInfoRepository extends JpaRepository<MemberBodyInfo, Long> {

    // 특정 회원의 기록 조회 (차트 데이터용) - member.id로 조회
    @Query("SELECT mbi FROM MemberBodyInfo mbi WHERE mbi.member.id = :memberId ORDER BY mbi.measuredTime DESC")
    List<MemberBodyInfo> findByMemberIdOrderByMeasuredTimeDesc(@Param("memberId") Long memberId);

    // (필요 시 사용) 특정 배송지로 보낸 기록 찾기
    List<MemberBodyInfo> findByShipZipcode(String shipZipcode);

    // 회원 ID로 신체 정보 조회
    @Query("SELECT mbi FROM MemberBodyInfo mbi WHERE mbi.member.id = :memberId")
    List<MemberBodyInfo> findByMemberId(@Param("memberId") Long memberId);

    // 회원 객체로 신체 정보 조회
    List<MemberBodyInfo> findByMember(Member member);
}