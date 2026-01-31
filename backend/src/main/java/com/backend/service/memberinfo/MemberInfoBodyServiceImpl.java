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

import java.util.ArrayList;
import java.util.List;
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

    @Override
    public BodyCompareFeedbackDTO saveAndCompare(Long memberId, MemberInfoBodyDTO dto) {
        log.info("신체 정보 저장 후 직전 데이터와 비교: memberId={}", memberId);

        // 1. 저장
        MemberInfoBody entity = dto.toEntity(memberId);
        MemberInfoBody saved = memberInfoBodyRepository.save(entity);

        // 2. 해당 회원 최신 2건 조회 (방금 저장한 것 = current, 그 이전 = previous)
        List<MemberInfoBody> history = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);
        MemberInfoBody current = history.isEmpty() ? null : history.get(0);
        MemberInfoBody previous = history.size() > 1 ? history.get(1) : null;

        return buildCompareFeedback(previous, current);
    }

    /**
     * 직전 1 row와 규칙 기반 비교 (체중 0.5kg, 체지방률 0.5%, 골격근량 0.2kg)
     * 식단/운동 7일 수집 없음 → mealFeedback, exerciseFeedback 빈 문자열 또는 안내 문구
     */
    private BodyCompareFeedbackDTO buildCompareFeedback(MemberInfoBody previous, MemberInfoBody current) {
        List<BodyCompareFeedbackDTO.BodyChangeItem> bodyChanges = new ArrayList<>();
        String summary;
        boolean hasComparison = (previous != null && current != null);

        if (hasComparison) {
            double wPrev = nullToZero(previous.getWeight());
            double wCur = nullToZero(current.getWeight());
            double diffW = wCur - wPrev;
            if (Math.abs(diffW) >= 0.5) {
                String dir = diffW > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("weight")
                        .change(dir)
                        .value(String.format("%s%.1fkg", diffW > 0 ? "+" : "", diffW))
                        .message(String.format("체중 %s (%.1fkg → %.1fkg)", dir, wPrev, wCur))
                        .build());
            }

            double fPrev = nullToZero(previous.getBodyFatPercent());
            double fCur = nullToZero(current.getBodyFatPercent());
            double diffF = fCur - fPrev;
            if (Math.abs(diffF) >= 0.5) {
                String dir = diffF > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("bodyFatPercent")
                        .change(dir)
                        .value(String.format("%s%.1f%%", diffF > 0 ? "+" : "", diffF))
                        .message(String.format("체지방률 %s (%.1f%% → %.1f%%)", dir, fPrev, fCur))
                        .build());
            }

            double mPrev = nullToZero(previous.getSkeletalMuscleMass());
            double mCur = nullToZero(current.getSkeletalMuscleMass());
            double diffM = mCur - mPrev;
            if (Math.abs(diffM) >= 0.2) {
                String dir = diffM > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("skeletalMuscleMass")
                        .change(dir)
                        .value(String.format("%s%.1fkg", diffM > 0 ? "+" : "", diffM))
                        .message(String.format("골격근량 %s (%.1fkg → %.1fkg)", dir, mPrev, mCur))
                        .build());
            }

            summary = bodyChanges.isEmpty()
                    ? "이전 측정과 큰 변화는 없습니다."
                    : "이전 대비 " + bodyChanges.size() + "가지 체성분 변화가 있습니다.";
        } else {
            // 분석만 (직전 데이터 없음)
            if (current != null) {
                List<String> parts = new ArrayList<>();
                if (current.getWeight() != null) parts.add("체중 " + current.getWeight() + "kg");
                if (current.getBodyFatPercent() != null) parts.add("체지방률 " + current.getBodyFatPercent() + "%");
                if (current.getSkeletalMuscleMass() != null) parts.add("골격근량 " + current.getSkeletalMuscleMass() + "kg");
                summary = parts.isEmpty() ? "측정값을 확인해보세요." : "현재 측정: " + String.join(", ", parts) + ".";
            } else {
                summary = "분석할 수치를 추출하지 못했습니다.";
            }
        }

        List<String> recommendations = new ArrayList<>();
        for (BodyCompareFeedbackDTO.BodyChangeItem item : bodyChanges) {
            if ("bodyFatPercent".equals(item.getType()) && "증가".equals(item.getChange())) {
                recommendations.add("유산소 운동을 꾸준히 해보세요.");
            } else if ("bodyFatPercent".equals(item.getType()) && "감소".equals(item.getChange())) {
                recommendations.add("체지방률이 줄어든 좋은 변화입니다.");
            } else if ("skeletalMuscleMass".equals(item.getType()) && "증가".equals(item.getChange())) {
                recommendations.add("근육량이 늘었습니다. 꾸준히 유지해보세요.");
            } else if ("skeletalMuscleMass".equals(item.getType()) && "감소".equals(item.getChange())) {
                recommendations.add("단백질 섭취와 근력 운동을 함께 해보세요.");
            }
        }

        return BodyCompareFeedbackDTO.builder()
                .summary(summary)
                .bodyChanges(bodyChanges)
                .mealFeedback("")
                .exerciseFeedback("")
                .recommendations(recommendations)
                .hasComparison(hasComparison)
                .build();
    }

    private static double nullToZero(Double v) {
        return v == null ? 0.0 : v;
    }
}

