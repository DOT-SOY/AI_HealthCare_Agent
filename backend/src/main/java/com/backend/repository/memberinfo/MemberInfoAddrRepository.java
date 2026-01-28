package com.backend.repository.memberinfo;

import com.backend.domain.memberinfo.MemberInfoAddr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberInfoAddrRepository extends JpaRepository<MemberInfoAddr, Long> {
    List<MemberInfoAddr> findAllByMemberIdOrderByIdDesc(Long memberId);

    Optional<MemberInfoAddr> findByMemberIdAndIsDefaultTrue(Long memberId);
}


