package com.backend.service.memberinfo;

import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.ExercisePurpose;
import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.exception.ResourceNotFoundException;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoAddrRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberInfoBodyServiceImpl implements MemberInfoBodyService {

    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final MemberInfoAddrRepository memberInfoAddrRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public MemberInfoBody create(MemberInfoBodyDTO dto) {
        Long memberId = Objects.requireNonNull(dto.getMemberId(), "memberId는 필수입니다.");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + memberId));

        MemberInfoBody info = new MemberInfoBody();
        info.setMember(member);
        info.setMeasuredTime(dto.getMeasuredTime() != null ? dto.getMeasuredTime() : LocalDateTime.now());

        info.setHeight(dto.getHeight());
        info.setWeight(dto.getWeight());
        info.setSkeletalMuscleMass(dto.getSkeletalMuscleMass());
        info.setBodyFatPercent(dto.getBodyFatPercent());
        info.setBodyWater(dto.getBodyWater());
        info.setProtein(dto.getProtein());
        info.setMinerals(dto.getMinerals());
        info.setBodyFatMass(dto.getBodyFatMass());
        info.setTargetWeight(dto.getTargetWeight());
        info.setWeightControl(dto.getWeightControl());
        info.setFatControl(dto.getFatControl());
        info.setMuscleControl(dto.getMuscleControl());

        if (dto.getPurpose() != null) {
            try {
                info.setPurpose(ExercisePurpose.valueOf(dto.getPurpose()));
            } catch (IllegalArgumentException e) {
                info.setPurpose(null);
            }
        }

        syncMemberHeightWeight(member, dto.getHeight(), dto.getWeight());

        return memberInfoBodyRepository.save(info);
    }

    @Override
    @Transactional
    public MemberInfoBody updateHeightWeight(Long id, MemberInfoBodyDTO dto) {
        Long safeId = Objects.requireNonNull(id, "id는 필수입니다.");
        MemberInfoBody info = memberInfoBodyRepository.findById(safeId)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + safeId));

        if (dto.getHeight() != null) {
            info.setHeight(dto.getHeight());
        }
        if (dto.getWeight() != null) {
            info.setWeight(dto.getWeight());
        }
        if (dto.getPurpose() != null) {
            try {
                info.setPurpose(ExercisePurpose.valueOf(dto.getPurpose()));
            } catch (IllegalArgumentException e) {
                info.setPurpose(null);
            }
        }

        if (dto.getHeight() != null || dto.getWeight() != null) {
            syncMemberHeightWeight(info.getMember(), dto.getHeight(), dto.getWeight());
        }

        return info;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long safeId = Objects.requireNonNull(id, "id는 필수입니다.");
        if (!memberInfoBodyRepository.existsById(safeId)) {
            throw new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + safeId);
        }
        memberInfoBodyRepository.deleteById(safeId);
    }

    @Override
    public List<MemberInfoBodyResponseDTO> getBodyInfoHistory(Long memberId) {
        Long safeMemberId = Objects.requireNonNull(memberId, "memberId는 필수입니다.");
        List<MemberInfoBody> entities = memberInfoBodyRepository.findAllByMemberIdOrderByMeasuredTimeAsc(safeMemberId);
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        MemberInfoAddr defaultAddr = memberInfoAddrRepository.findByMemberIdAndIsDefaultTrue(safeMemberId).orElse(null);

        return entities.stream()
                .map(entity -> toResponse(entity, defaultAddr))
                .collect(Collectors.toList());
    }

    @Override
    public List<MemberInfoBodyResponseDTO> getBodyInfoHistoryByEmail(String email) {
        List<MemberInfoBody> entities = memberInfoBodyRepository.findAllByMemberEmailOrderByMeasuredTimeAsc(email);
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        Long memberId = Objects.requireNonNull(entities.get(0).getMember().getId(), "memberId를 찾을 수 없습니다.");
        MemberInfoAddr defaultAddr = memberInfoAddrRepository.findByMemberIdAndIsDefaultTrue(memberId).orElse(null);

        return entities.stream()
                .map(entity -> toResponse(entity, defaultAddr))
                .collect(Collectors.toList());
    }

    private void syncMemberHeightWeight(Member member, Double height, Double weight) {
        if (height != null) {
            member.setHeight((int) Math.round(height));
        }
        if (weight != null) {
            member.setWeight(weight);
        }
    }

    private MemberInfoBodyResponseDTO toResponse(MemberInfoBody entity, MemberInfoAddr defaultAddr) {
        return MemberInfoBodyResponseDTO.builder()
                .id(entity.getId())
                .measuredTime(entity.getMeasuredTime())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getName())
                .gender(entity.getMember().getGender())
                .birthDate(entity.getMember().getBirthDate())
                .height(entity.getHeight())
                .weight(entity.getWeight())
                .skeletalMuscleMass(entity.getSkeletalMuscleMass())
                .bodyFatPercent(entity.getBodyFatPercent())
                .bodyWater(entity.getBodyWater())
                .protein(entity.getProtein())
                .minerals(entity.getMinerals())
                .bodyFatMass(entity.getBodyFatMass())
                .targetWeight(entity.getTargetWeight())
                .weightControl(entity.getWeightControl())
                .fatControl(entity.getFatControl())
                .muscleControl(entity.getMuscleControl())
                .purpose(entity.getPurpose() != null ? entity.getPurpose().name() : null)
                .defaultAddrId(defaultAddr != null ? defaultAddr.getId() : null)
                .shipToName(defaultAddr != null ? defaultAddr.getRecipientName() : null)
                .shipToPhone(defaultAddr != null ? defaultAddr.getRecipientPhone() : null)
                .shipZipcode(defaultAddr != null ? defaultAddr.getZipcode() : null)
                .shipAddress1(defaultAddr != null ? defaultAddr.getAddress1() : null)
                .shipAddress2(defaultAddr != null ? defaultAddr.getAddress2() : null)
                .build();
    }
}

