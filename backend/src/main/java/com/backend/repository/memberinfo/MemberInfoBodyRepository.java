package com.backend.repository.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberInfoBodyRepository extends JpaRepository<MemberInfoBody, Long> {
    List<MemberInfoBody> findAllByMemberIdOrderByMeasuredTimeAsc(Long memberId);

    @Query("SELECT mib FROM MemberInfoBody mib WHERE mib.member.email = :email ORDER BY mib.measuredTime ASC")
    List<MemberInfoBody> findAllByMemberEmailOrderByMeasuredTimeAsc(@Param("email") String email);
}


