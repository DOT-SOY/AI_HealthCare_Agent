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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                .findFirstByMemberIdAndDeletedAtIsNullOrderByMeasuredTimeDescCreatedAtDesc(memberId)
                .orElse(null);

        // Member 정보 조회
        Member member = memberRepository.findById(memberId).orElse(null);

        return MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyResponseDTO getBodyInfoByDateAndMetric(Long memberId, LocalDate date, String metric) {
        log.info("인바디 조회 요청: memberId={}, date={}, metric={}", memberId, date, metric);

        LocalDate targetDate = date != null ? date : LocalDate.now();
        
        // 날짜의 시작과 끝 시간 계산
        Instant dateStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dateEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        
        Optional<MemberInfoBody> entityOpt = memberInfoBodyRepository.findByMemberIdAndDate(
            memberId, dateStart, dateEnd
        );
        
        if (entityOpt.isEmpty()) {
            log.info("해당 날짜의 인바디 기록이 없습니다: memberId={}, date={}", memberId, targetDate);
            return null;
        }
        
        MemberInfoBody entity = entityOpt.get();
        Member member = memberRepository.findById(memberId).orElse(null);
        MemberInfoBodyResponseDTO dto = MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
        
        // 특정 항목만 조회하는 경우 필터링
        if (metric != null && !metric.trim().isEmpty()) {
            MemberInfoBodyResponseDTO filteredDto = MemberInfoBodyResponseDTO.builder()
                .id(dto.getId())
                .memberId(dto.getMemberId())
                .measuredTime(dto.getMeasuredTime())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .memberName(dto.getMemberName())
                .gender(dto.getGender())
                .birthDate(dto.getBirthDate())
                .build();
            
            switch (metric.toUpperCase()) {
                case "BODY_FAT":
                    filteredDto.setBodyFatPercent(dto.getBodyFatPercent());
                    filteredDto.setBodyFatMass(dto.getBodyFatMass());
                    break;
                case "SKELETAL_MUSCLE":
                    filteredDto.setSkeletalMuscleMass(dto.getSkeletalMuscleMass());
                    break;
                case "WEIGHT":
                    filteredDto.setWeight(dto.getWeight());
                    break;
                default:
                    log.warn("알 수 없는 metric: {}, 모든 항목 반환", metric);
                    return dto;
            }
            
            return filteredDto;
        }
        
        // 모든 항목 반환
        return dto;
    }
}

