package com.backend.service.memberbodyinfo;

import com.backend.domain.memberbodyinfo.ExercisePurpose;
import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import com.backend.domain.member.Member;
import com.backend.dto.memberbodyinfo.MemberBodyInfoDTO;
import com.backend.dto.memberbodyinfo.MemberBodyInfoResponseDTO;
import com.backend.exception.ResourceNotFoundException;
import com.backend.repository.memberbodyinfo.MemberBodyInfoRepository;
import com.backend.repository.member.MemberRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberBodyInfoServiceImpl implements MemberBodyInfoService {

    private final MemberBodyInfoRepository memberBodyInfoRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public MemberBodyInfo create(MemberBodyInfoDTO dto) {
        // 회원 존재 확인 (memberId는 이메일 등 유니크한 값으로 가정)
        Member member = memberRepository.findById(dto.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("회원을 찾을 수 없습니다. ID: " + dto.getMemberId()));

        // Entity 생성 및 매핑 (Builder 패턴 사용 권장하지만 여기선 Setter 사용)
        MemberBodyInfo info = new MemberBodyInfo();
        info.setMember(member);
        info.setMeasuredTime(dto.getMeasuredTime());

        // 1. 기본 정보
        info.setHeight(dto.getHeight());
        info.setWeight(dto.getWeight());
        info.setSkeletalMuscleMass(dto.getSkeletalMuscleMass());
        info.setBodyFatPercent(dto.getBodyFatPercent());

        // 2. 체성분 상세
        info.setBodyWater(dto.getBodyWater());
        info.setProtein(dto.getProtein());
        info.setMinerals(dto.getMinerals());
        info.setBodyFatMass(dto.getBodyFatMass());

        // 3. 체중 조절
        info.setTargetWeight(dto.getTargetWeight());
        info.setWeightControl(dto.getWeightControl());
        info.setFatControl(dto.getFatControl());
        info.setMuscleControl(dto.getMuscleControl());

        // 4. 배송 정보
        info.setShipToName(dto.getShipToName());
        info.setShipToPhone(dto.getShipToPhone());
        info.setShipZipcode(dto.getShipZipcode());
        info.setShipAddress1(dto.getShipAddress1());
        info.setShipAddress2(dto.getShipAddress2());

        // 5. 기타 (String -> Enum 변환 안전하게 처리)
        info.setNotes(dto.getNotes());
        if (dto.getPurpose() != null) {
            try {
                info.setPurpose(ExercisePurpose.valueOf(dto.getPurpose()));
            } catch (IllegalArgumentException e) {
                // 유효하지 않은 문자열이면 null 처리 하거나 기본값 설정
                info.setPurpose(null);
            }
        }

        return memberBodyInfoRepository.save(info);
    }

    @Override
    public MemberBodyInfo findById(Long id) {
        return memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));
    }

    @Override
    public List<MemberBodyInfo> findByMemberId(Long memberId) {
        // 이 로직은 Repository 메서드에 의존합니다. (member 객체 대신 memberId로 바로 조회)
        // memberRepository 조회 없이 바로 Repository의 findByMemberIdOrderBy... 사용 권장
        return memberBodyInfoRepository.findByMemberIdOrderByMeasuredTimeDesc(memberId);
    }

    @Override
    public List<MemberBodyInfo> findAll() {
        return memberBodyInfoRepository.findAll();
    }

    @Override
    @Transactional
    public MemberBodyInfo update(Long id, MemberBodyInfoDTO dto) {
        MemberBodyInfo info = memberBodyInfoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id));

        // 필드 업데이트 (null 체크 후 반영)
        // 측정 일시는 보통 변경하지 않지만 필요시 수정
        if (dto.getMeasuredTime() != null) info.setMeasuredTime(dto.getMeasuredTime());

        // 1. 기본 정보
        if (dto.getHeight() != null) info.setHeight(dto.getHeight());
        if (dto.getWeight() != null) info.setWeight(dto.getWeight());
        if (dto.getSkeletalMuscleMass() != null) info.setSkeletalMuscleMass(dto.getSkeletalMuscleMass());
        if (dto.getBodyFatPercent() != null) info.setBodyFatPercent(dto.getBodyFatPercent());

        // 2. 체성분 상세
        if (dto.getBodyWater() != null) info.setBodyWater(dto.getBodyWater());
        if (dto.getProtein() != null) info.setProtein(dto.getProtein());
        if (dto.getMinerals() != null) info.setMinerals(dto.getMinerals());
        if (dto.getBodyFatMass() != null) info.setBodyFatMass(dto.getBodyFatMass());

        // 3. 체중 조절
        if (dto.getTargetWeight() != null) info.setTargetWeight(dto.getTargetWeight());
        if (dto.getWeightControl() != null) info.setWeightControl(dto.getWeightControl());
        if (dto.getFatControl() != null) info.setFatControl(dto.getFatControl());
        if (dto.getMuscleControl() != null) info.setMuscleControl(dto.getMuscleControl());

        // 4. 배송 정보
        if (dto.getShipToName() != null) info.setShipToName(dto.getShipToName());
        if (dto.getShipToPhone() != null) info.setShipToPhone(dto.getShipToPhone());
        if (dto.getShipZipcode() != null) info.setShipZipcode(dto.getShipZipcode());
        if (dto.getShipAddress1() != null) info.setShipAddress1(dto.getShipAddress1());
        if (dto.getShipAddress2() != null) info.setShipAddress2(dto.getShipAddress2());

        // 5. 기타
        if (dto.getNotes() != null) info.setNotes(dto.getNotes());
        if (dto.getPurpose() != null) {
            try {
                info.setPurpose(ExercisePurpose.valueOf(dto.getPurpose()));
            } catch (IllegalArgumentException e) {
                // 예외 무시 또는 처리
            }
        }

        // Jpa는 트랜잭션 종료 시 Dirty Checking으로 자동 저장되지만 명시적 리턴
        return info;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!memberBodyInfoRepository.existsById(id)) {
            throw new ResourceNotFoundException("신체 정보를 찾을 수 없습니다. ID: " + id);
        }
        memberBodyInfoRepository.deleteById(id);
    }
    @Override
    public List<MemberBodyInfoResponseDTO> getBodyInfoHistory(Long memberId) {
        List<MemberBodyInfo> entities = memberBodyInfoRepository.findAllByMemberIdOrderByMeasuredTimeAsc(memberId);

        return entities.stream()
                .map(e -> MemberBodyInfoResponseDTO.builder()
                        .id(e.getId())
                        .measuredTime(e.getMeasuredTime())
                        .weight(e.getWeight())
                        .skeletalMuscleMass(e.getSkeletalMuscleMass())
                        .bodyFatPercent(e.getBodyFatPercent())
                        .bodyWater(e.getBodyWater())
                        .protein(e.getProtein())
                        .minerals(e.getMinerals())
                        .bodyFatMass(e.getBodyFatMass())
                        // ✅ [추가] 조절 데이터 매핑
                        .targetWeight(e.getTargetWeight())
                        .weightControl(e.getWeightControl())
                        .fatControl(e.getFatControl())
                        .muscleControl(e.getMuscleControl())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<MemberBodyInfoResponseDTO> getBodyInfoHistoryByEmail(String email) {
        // 이메일로 조회
        List<MemberBodyInfo> entities = memberBodyInfoRepository.findAllByMemberEmailOrderByMeasuredTimeAsc(email);

        return entities.stream()
                .map(e -> MemberBodyInfoResponseDTO.builder()
                        .id(e.getId())
                        .measuredTime(e.getMeasuredTime())
                        // ✅ 회원 정보 매핑
                        .memberName(e.getMember().getName())
                        .gender(e.getMember().getGender())
                        .birthDate(e.getMember().getBirthDate())
                        // ✅ 신체 정보 매핑
                        .height(e.getHeight()) // 키는 매번 잴 수 있으니 이력 데이터 사용
                        .weight(e.getWeight())
                        .skeletalMuscleMass(e.getSkeletalMuscleMass())
                        .bodyFatPercent(e.getBodyFatPercent())
                        .bodyWater(e.getBodyWater())
                        .protein(e.getProtein())
                        .minerals(e.getMinerals())
                        .bodyFatMass(e.getBodyFatMass())
                        .targetWeight(e.getTargetWeight())
                        .weightControl(e.getWeightControl())
                        .fatControl(e.getFatControl())
                        .muscleControl(e.getMuscleControl())
                        .shipToName(e.getShipToName())
                        .shipToPhone(e.getShipToPhone())
                        .shipZipcode(e.getShipZipcode())
                        .shipAddress1(e.getShipAddress1())
                        .shipAddress2(e.getShipAddress2())
                        .build())
                .collect(Collectors.toList());
    }
}