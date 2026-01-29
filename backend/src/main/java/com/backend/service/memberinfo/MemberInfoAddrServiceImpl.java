package com.backend.service.memberinfo;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.dto.memberinfo.MemberInfoAddrDTO;
import com.backend.repository.memberinfo.MemberInfoAddrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class MemberInfoAddrServiceImpl implements MemberInfoAddrService {

    private final MemberInfoAddrRepository memberInfoAddrRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MemberInfoAddrDTO> getList(Long memberId) {
        log.info("배송지 목록 조회 요청: memberId={}", memberId);

        List<MemberInfoAddr> entities = memberInfoAddrRepository
                .findByMemberIdOrderByDefaultDesc(memberId);

        return entities.stream()
                .map(MemberInfoAddrDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Long create(Long memberId, MemberInfoAddrDTO dto) {
        log.info("배송지 생성 요청: memberId={}", memberId);

        // 기본 배송지로 설정하는 경우, 기존 기본 배송지 해제
        if (dto.getIsDefault() != null && dto.getIsDefault()) {
            memberInfoAddrRepository.findDefaultByMemberId(memberId)
                    .ifPresent(addr -> {
                        addr.unsetDefault();
                        memberInfoAddrRepository.save(addr);
                    });
        }

        MemberInfoAddr entity = dto.toEntity(memberId);
        MemberInfoAddr saved = memberInfoAddrRepository.save(entity);

        log.info("배송지 생성 완료: id={}", saved.getId());
        return saved.getId();
    }

    @Override
    public MemberInfoAddrDTO update(Long id, MemberInfoAddrDTO dto) {
        log.info("배송지 수정 요청: id={}", id);

        MemberInfoAddr entity = memberInfoAddrRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        // 기본 배송지로 설정하는 경우, 기존 기본 배송지 해제
        if (dto.getIsDefault() != null && dto.getIsDefault() && !entity.getIsDefault()) {
            memberInfoAddrRepository.findDefaultByMemberId(entity.getMemberId())
                    .ifPresent(addr -> {
                        if (!addr.getId().equals(id)) {
                            addr.unsetDefault();
                            memberInfoAddrRepository.save(addr);
                        }
                    });
        }

        entity.update(
                dto.getShipToName(), dto.getShipToPhone(), dto.getShipZipcode(),
                dto.getShipAddress1(), dto.getShipAddress2()
        );

        if (dto.getIsDefault() != null) {
            if (dto.getIsDefault()) {
                entity.setDefault();
            } else {
                entity.unsetDefault();
            }
        }

        MemberInfoAddr saved = memberInfoAddrRepository.save(entity);
        log.info("배송지 수정 완료: id={}", id);

        return MemberInfoAddrDTO.fromEntity(saved);
    }

    @Override
    public MemberInfoAddrDTO setDefault(Long id) {
        log.info("기본 배송지 설정 요청: id={}", id);

        MemberInfoAddr entity = memberInfoAddrRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        // 기존 기본 배송지 해제
        memberInfoAddrRepository.findDefaultByMemberId(entity.getMemberId())
                .ifPresent(addr -> {
                    if (!addr.getId().equals(id)) {
                        addr.unsetDefault();
                        memberInfoAddrRepository.save(addr);
                    }
                });

        entity.setDefault();
        MemberInfoAddr saved = memberInfoAddrRepository.save(entity);

        log.info("기본 배송지 설정 완료: id={}", id);
        return MemberInfoAddrDTO.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        log.info("배송지 삭제 요청: id={}", id);

        MemberInfoAddr entity = memberInfoAddrRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        memberInfoAddrRepository.delete(entity);

        log.info("배송지 삭제 완료: id={}", id);
    }
}

