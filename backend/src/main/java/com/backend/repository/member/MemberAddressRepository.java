package com.backend.repository.member;

import com.backend.domain.member.MemberAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberAddressRepository extends JpaRepository<MemberAddress, Long> {

    List<MemberAddress> findByMember_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long memberId);

    Optional<MemberAddress> findByIdAndMember_IdAndDeletedAtIsNull(Long id, Long memberId);
}

