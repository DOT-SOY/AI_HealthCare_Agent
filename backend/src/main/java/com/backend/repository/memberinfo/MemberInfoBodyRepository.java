package com.backend.repository.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberInfoBodyRepository extends JpaRepository<MemberInfoBody, Long> {

    // 특정 회원의 신체 정보 이력 조회 (삭제되지 않은 것만, 최신순)
    @Query("SELECT m FROM MemberInfoBody m WHERE m.memberId = :memberId AND m.deletedAt IS NULL ORDER BY m.measuredTime DESC, m.createdAt DESC")
    List<MemberInfoBody> findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(@Param("memberId") Long memberId);

    // 특정 회원의 최신 신체 정보 조회
    @Query("SELECT m FROM MemberInfoBody m WHERE m.memberId = :memberId AND m.deletedAt IS NULL ORDER BY m.measuredTime DESC, m.createdAt DESC")
    Optional<MemberInfoBody> findTopByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(@Param("memberId") Long memberId);

    // ID로 조회 (삭제되지 않은 것만)
    @Query("SELECT m FROM MemberInfoBody m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MemberInfoBody> findByIdAndNotDeleted(@Param("id") Long id);
}

