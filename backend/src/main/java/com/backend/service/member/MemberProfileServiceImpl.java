package com.backend.service.member;

import com.backend.dto.member.MemberProfileResponse;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.service.memberinfo.MemberInfoBodyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 프로필 조회 서비스 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberProfileServiceImpl implements MemberProfileService {

    private final CurrentMemberService currentMemberService;
    private final MemberInfoBodyService memberInfoBodyService;

    @Override
    public MemberProfileResponse getMyProfile() {
        var member = currentMemberService.getCurrentMemberOrThrow();
        Long memberId = member.getId();

        log.info("사용자 프로필 조회: memberId={}", memberId);

        // 최신 신체 정보 조회 (기존 서비스 재사용)
        MemberInfoBodyResponseDTO bodyInfo = memberInfoBodyService.getLatest(memberId);

        // 프로필 응답 생성
        MemberProfileResponse.MemberProfileResponseBuilder builder = MemberProfileResponse.builder()
                .name(member.getName())
                .email(member.getEmail())
                .gender(member.getGender() != null ? member.getGender().name() : null);

        if (bodyInfo != null) {
            builder.heightCm(bodyInfo.getHeight())
                   .weightKg(bodyInfo.getWeight())
                   .bodyFatPercent(bodyInfo.getBodyFatPercent())
                   .bodyWaterPercent(bodyInfo.getBodyWater())
                   .goal(bodyInfo.getExercisePurpose());
        }

        // 알러지 및 회피 성분 (현재는 빈 리스트, 추후 확장 가능)
        // TODO: Member 엔티티에 allergies 필드가 추가되면 여기서 조회
        List<String> allergies = new ArrayList<>();
        List<String> avoid = new ArrayList<>();

        // goal이 DIET인 경우 카페인 회피 추가 (예시)
        if (bodyInfo != null && bodyInfo.getExercisePurpose() != null) {
            // 사용자 프로필에서 회피 정보를 추출하는 로직 추가 가능
        }

        builder.allergies(allergies)
               .avoid(avoid);

        return builder.build();
    }
}
