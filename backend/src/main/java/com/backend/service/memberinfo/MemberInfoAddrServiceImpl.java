package com.backend.service.memberinfo;

import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.dto.memberinfo.MemberInfoAddrDTO;
import com.backend.exception.ResourceNotFoundException;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoAddrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberInfoAddrServiceImpl implements MemberInfoAddrService {

    private final MemberInfoAddrRepository memberInfoAddrRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public MemberInfoAddr create(MemberInfoAddrDTO dto) {
        Long memberId = Objects.requireNonNull(dto.getMemberId(), "memberId는 필수입니다.");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + memberId));

        boolean requestedDefault = Boolean.TRUE.equals(dto.getIsDefault());
        boolean hasDefault = memberInfoAddrRepository.findByMemberIdAndIsDefaultTrue(memberId).isPresent();

        MemberInfoAddr addr = new MemberInfoAddr();
        addr.setMember(member);
        addr.setRecipientName(dto.getRecipientName());
        addr.setRecipientPhone(dto.getRecipientPhone());
        addr.setZipcode(dto.getZipcode());
        addr.setAddress1(dto.getAddress1());
        addr.setAddress2(dto.getAddress2());

        if (requestedDefault || !hasDefault) {
            clearDefault(memberId);
            addr.setDefault(true);
        } else {
            addr.setDefault(false);
        }

        return memberInfoAddrRepository.save(addr);
    }

    @Override
    @Transactional
    public MemberInfoAddr update(Long id, MemberInfoAddrDTO dto) {
        Long safeId = Objects.requireNonNull(id, "id는 필수입니다.");
        MemberInfoAddr addr = memberInfoAddrRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("배송지 정보를 찾을 수 없습니다. ID: " + safeId));

        if (dto.getRecipientName() != null) addr.setRecipientName(dto.getRecipientName());
        if (dto.getRecipientPhone() != null) addr.setRecipientPhone(dto.getRecipientPhone());
        if (dto.getZipcode() != null) addr.setZipcode(dto.getZipcode());
        if (dto.getAddress1() != null) addr.setAddress1(dto.getAddress1());
        if (dto.getAddress2() != null) addr.setAddress2(dto.getAddress2());

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefault(Objects.requireNonNull(addr.getMember().getId(), "memberId를 찾을 수 없습니다."));
            addr.setDefault(true);
        }

        return addr;
    }

    @Override
    @Transactional
    public MemberInfoAddr setDefault(Long id) {
        Long safeId = Objects.requireNonNull(id, "id는 필수입니다.");
        MemberInfoAddr addr = memberInfoAddrRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("배송지 정보를 찾을 수 없습니다. ID: " + safeId));

        clearDefault(Objects.requireNonNull(addr.getMember().getId(), "memberId를 찾을 수 없습니다."));
        addr.setDefault(true);
        return addr;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long safeId = Objects.requireNonNull(id, "id는 필수입니다.");
        MemberInfoAddr addr = memberInfoAddrRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("배송지 정보를 찾을 수 없습니다. ID: " + safeId));

        Long memberId = Objects.requireNonNull(addr.getMember().getId(), "memberId를 찾을 수 없습니다.");
        boolean wasDefault = addr.isDefault();
        memberInfoAddrRepository.delete(addr);

        if (wasDefault) {
            List<MemberInfoAddr> remaining = memberInfoAddrRepository.findAllByMemberIdOrderByIdDesc(memberId);
            if (!remaining.isEmpty()) {
                remaining.get(0).setDefault(true);
            }
        }
    }

    @Override
    public List<MemberInfoAddr> getList(Long memberId) {
        return memberInfoAddrRepository.findAllByMemberIdOrderByIdDesc(memberId);
    }

    private void clearDefault(Long memberId) {
        memberInfoAddrRepository.findByMemberIdAndIsDefaultTrue(memberId)
                .ifPresent(addr -> addr.setDefault(false));
    }

}

