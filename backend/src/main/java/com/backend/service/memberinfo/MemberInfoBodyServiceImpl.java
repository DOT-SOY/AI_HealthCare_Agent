package com.backend.service.memberinfo;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.BodyCompareFeedbackDTO;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    public BodyCompareFeedbackDTO saveAndCompare(Long memberId, MemberInfoBodyDTO dto) {
        log.info("OCR 저장 및 비교 요청: memberId={}", memberId);
        dto.setId(null);
        MemberInfoBody entity = dto.toEntity(memberId);
        memberInfoBodyRepository.save(entity);

        List<MemberInfoBody> history = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);
        MemberInfoBody current = history.isEmpty() ? null : history.get(0);
        MemberInfoBody previous = history.size() < 2 ? null : history.get(1);

        return buildCompareFeedback(current, previous);
    }

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

        MemberInfoBody existingEntity = memberInfoBodyRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        Long memberId = existingEntity.getMemberId();

        dto.setId(null);
        dto.setMeasuredTime(Instant.now());
        MemberInfoBody newEntity = dto.toEntity(memberId);

        MemberInfoBody saved = memberInfoBodyRepository.save(newEntity);
        log.info("신체 정보 새 레코드 생성 완료: id={}, memberId={}", saved.getId(), memberId);

        // Member 정보 조회
        Member member = memberRepository.findById(memberId).orElse(null);

        return MemberInfoBodyResponseDTO.fromEntityWithMember(saved, member);
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

        List<MemberInfoBody> bodyList = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);
        MemberInfoBody entity = bodyList.isEmpty() ? null : bodyList.get(0);

        Member member = memberRepository.findById(memberId).orElse(null);

        if (entity == null) {
            log.debug("최신 신체 정보 없음: memberId={} (삭제되지 않은 건수=0)", memberId);
        }
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

    private BodyCompareFeedbackDTO buildCompareFeedback(MemberInfoBody current, MemberInfoBody previous) {
        if (current == null) {
            return BodyCompareFeedbackDTO.builder()
                    .summary("저장되었습니다.")
                    .bodyChanges(List.of())
                    .recommendations(List.of())
                    .build();
        }
        if (previous == null) {
            return BodyCompareFeedbackDTO.builder()
                    .summary("인바디 결과가 저장되었습니다. 다음 측정부터 직전 기록과 비교 분석을 제공합니다.")
                    .bodyChanges(List.of())
                    .recommendations(List.of())
                    .build();
        }

        List<BodyCompareFeedbackDTO.BodyChangeItem> bodyChanges = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        compareDouble(bodyChanges, "체중", current.getWeight(), previous.getWeight(), "kg");
        compareDouble(bodyChanges, "골격근량", current.getSkeletalMuscleMass(), previous.getSkeletalMuscleMass(), "kg");
        compareDouble(bodyChanges, "체지방률", current.getBodyFatPercent(), previous.getBodyFatPercent(), "%");

        String prevDateStr = formatMeasuredDate(previous.getMeasuredTime());
        String currDateStr = formatMeasuredDate(current.getMeasuredTime());
        String summary = bodyChanges.isEmpty()
                ? (prevDateStr + " 측정 대비 " + currDateStr + " 측정에서 유의한 변화가 없습니다.")
                : (prevDateStr + " 측정 대비 " + currDateStr + " 측정에서 " + bodyChanges.size() + "개 항목이 변경되었습니다.");

        if (current.getWeight() != null && previous.getWeight() != null) {
            double diff = current.getWeight() - previous.getWeight();
            if (diff > 0.5) recommendations.add("체중이 소폭 증가했습니다. 일주일 식단·운동을 유지해 보세요.");
            else if (diff < -0.5) recommendations.add("체중이 감소했습니다. 균형 잡힌 식사로 영양을 챙기세요.");
        }

        return BodyCompareFeedbackDTO.builder()
                .summary(summary)
                .bodyChanges(bodyChanges)
                .mealFeedback(null)
                .exerciseFeedback(null)
                .recommendations(recommendations)
                .build();
    }

    private String formatMeasuredDate(Instant measuredTime) {
        if (measuredTime == null) return "?";
        return measuredTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d"));
    }

    private void compareDouble(List<BodyCompareFeedbackDTO.BodyChangeItem> out, String label,
                               Double currentVal, Double previousVal, String unit) {
        if (currentVal == null || previousVal == null) return;
        double diff = currentVal - previousVal;
        if (Math.abs(diff) < 0.1) return;
        String change = diff > 0 ? "증가" : "감소";
        String message = String.format("%s %.1f%s → %.1f%s (%+.1f%s %s)", label, previousVal, unit, currentVal, unit, diff, unit, change);
        out.add(BodyCompareFeedbackDTO.BodyChangeItem.builder().message(message).change(change).build());
    }
}

