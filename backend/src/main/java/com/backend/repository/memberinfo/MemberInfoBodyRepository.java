package com.backend.repository.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberInfoBodyRepository extends JpaRepository<MemberInfoBody, Long> {

    // 특정 회원의 신체 정보 이력 조회 (삭제되지 않은 것만, 최신순)
    @Query("SELECT m FROM MemberInfoBody m WHERE m.memberId = :memberId AND m.deletedAt IS NULL ORDER BY m.measuredTime DESC, m.createdAt DESC")
    List<MemberInfoBody> findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(@Param("memberId") Long memberId);

    // 특정 회원의 최신 신체 정보 조회 (첫 번째 결과만)
    Optional<MemberInfoBody> findFirstByMemberIdAndDeletedAtIsNullOrderByMeasuredTimeDescCreatedAtDesc(Long memberId);

    // ID로 조회 (삭제되지 않은 것만)
    @Query("SELECT m FROM MemberInfoBody m WHERE m.id = :id AND m.deletedAt IS NULL")
    Optional<MemberInfoBody> findByIdAndNotDeleted(@Param("id") Long id);

    // 특정 날짜의 신체 정보 조회 (삭제되지 않은 것만, 최신순)
    @Query("SELECT m FROM MemberInfoBody m WHERE m.memberId = :memberId " +
           "AND m.measuredTime >= :dateStart AND m.measuredTime < :dateEnd " +
           "AND m.deletedAt IS NULL " +
           "ORDER BY m.measuredTime DESC, m.createdAt DESC")
    Optional<MemberInfoBody> findByMemberIdAndDate(
        @Param("memberId") Long memberId,
        @Param("dateStart") Instant dateStart,
        @Param("dateEnd") Instant dateEnd
    );

    /**
     * 운동 목적이 설정된 회원 ID 목록 (랭킹 대상)
     */
    @Query("SELECT DISTINCT m.memberId FROM MemberInfoBody m WHERE m.deletedAt IS NULL AND m.exercisePurpose IS NOT NULL")
    List<Long> findDistinctMemberIdsWithPurpose();

    /**
     * 회원별 최신 (memberId, exercisePurpose) 한 번에 조회 (N+1 방지)
     */
    @Query(value = """
        SELECT m.member_id, m.goal_type FROM member_info_body m
        INNER JOIN (
            SELECT member_id, MAX(measured_time) AS max_time FROM member_info_body
            WHERE deleted_at IS NULL AND goal_type IS NOT NULL
            GROUP BY member_id
        ) t ON m.member_id = t.member_id AND m.measured_time = t.max_time
        WHERE m.deleted_at IS NULL AND m.goal_type IS NOT NULL
        """, nativeQuery = true)
    List<Object[]> findLatestMemberIdAndPurpose();
}


