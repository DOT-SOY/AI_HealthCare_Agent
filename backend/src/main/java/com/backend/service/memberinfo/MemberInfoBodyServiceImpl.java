package com.backend.service.memberinfo;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class MemberInfoBodyServiceImpl implements MemberInfoBodyService {

    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final MemberRepository memberRepository;

    @Override
    public Long create(Long memberId, MemberInfoBodyDTO dto) {
        log.info("신체 정보 생성 요청: memberId={}", memberId);

        MemberInfoBody entity = dto.toEntity(memberId);
        MemberInfoBody saved = memberInfoBodyRepository.save(entity);

        log.info("신체 정보 생성 완료: id={}", saved.getId());
        return saved.getId();
    }

    @Override
    public MemberInfoBodyResponseDTO update(Long id, MemberInfoBodyDTO dto) {
        log.info("신체 정보 수정 요청: id={}", id);

        MemberInfoBody entity = memberInfoBodyRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        entity.update(
                dto.getHeight(), dto.getWeight(),
                dto.getSkeletalMuscleMass(), dto.getBodyFatPercent(),
                dto.getBodyWater(), dto.getProtein(), dto.getMinerals(), dto.getBodyFatMass(),
                dto.getTargetWeight(), dto.getWeightControl(), dto.getFatControl(), dto.getMuscleControl(),
                dto.getExercisePurpose()
        );

        MemberInfoBody saved = memberInfoBodyRepository.save(entity);
        log.info("신체 정보 수정 완료: id={}", id);

        return MemberInfoBodyResponseDTO.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        log.info("신체 정보 삭제 요청: id={}", id);

        MemberInfoBody entity = memberInfoBodyRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        entity.softDelete();
        memberInfoBodyRepository.save(entity);

        log.info("신체 정보 삭제 완료: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberInfoBodyResponseDTO> getHistory(Long memberId) {
        log.info("신체 정보 이력 조회 요청: memberId={}", memberId);

        List<MemberInfoBody> entities = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);

        // Member 정보 조회 (한 번만 조회)
        Member member = memberRepository.findById(memberId).orElse(null);

        return entities.stream()
                .map(entity -> MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyResponseDTO getLatest(Long memberId) {
        log.info("최신 신체 정보 조회 요청: memberId={}", memberId);

        MemberInfoBody entity = memberInfoBodyRepository
                .findTopByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId)
                .orElse(null);

        // Member 정보 조회
        Member member = memberRepository.findById(memberId).orElse(null);

        return MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
    }
}

