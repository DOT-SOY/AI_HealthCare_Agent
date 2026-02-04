package com.backend.service.ai.chat;

import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.dto.response.AIChatResponse;
import com.backend.dto.response.IntentClassificationResult;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.memberinfo.MemberInfoBodyService;
import com.backend.util.AIChatUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * BODY_QUERY 의도 처리 서비스 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BodyChatServiceImpl implements BodyChatService {

    private final MemberInfoBodyService memberInfoBodyService;
    private final CurrentMemberService currentMemberService;

    @Override
    public AIChatResponse handleBodyQuery(IntentClassificationResult classification) {
        var entities = classification.getEntities();
        Object dateObj = entities != null ? entities.get("date") : null;
        Object bodyMetricObj = entities != null ? entities.get("body_metric") : null;

        LocalDate targetDate = AIChatUtils.resolveDate(dateObj);
        String metric = bodyMetricObj != null ? bodyMetricObj.toString() : null;

        Long memberId = currentMemberService.getCurrentMemberOrThrow().getId();
        MemberInfoBodyResponseDTO bodyInfo = memberInfoBodyService.getBodyInfoByDateAndMetric(memberId, targetDate, metric);
        
        // 해당 날짜에 기록이 없으면 최신 기록 조회
        boolean isLatest = false;
        if (bodyInfo == null) {
            MemberInfoBodyResponseDTO latestInfo = memberInfoBodyService.getLatest(memberId);
            if (latestInfo != null) {
                // metric 필터링 적용
                bodyInfo = filterByMetric(latestInfo, metric);
                isLatest = true;
            }
        }

        String message = formatBodyMessage(bodyInfo, targetDate, metric, isLatest);

        return AIChatResponse.builder()
            .message(message)
            .intent("BODY_QUERY")
            .data(bodyInfo)
            .build();
    }

    /**
     * 인바디 조회 결과를 자연어 메시지로 포맷팅
     */
    private String formatBodyMessage(MemberInfoBodyResponseDTO bodyInfo, LocalDate date, String metric, boolean isLatest) {
        StringBuilder sb = new StringBuilder();

        if (bodyInfo == null) {
            String dateStr = AIChatUtils.formatDateForMessage(date);
            sb.append(dateStr).append(" 인바디 기록이 등록되어 있지 않아요.");
            sb.append(" 정기적인 체성분 측정으로 건강 관리를 더 체계적으로 해보세요! 📊");
            return sb.toString();
        }

        // 최신 기록인 경우 날짜 표시
        if (isLatest) {
            if (bodyInfo.getMeasuredTime() != null) {
                LocalDate recordDate = bodyInfo.getMeasuredTime()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
                String recordDateStr = AIChatUtils.formatDateForMessage(recordDate);
                sb.append(recordDateStr).append("의 최신 인바디 기록을 확인했어요!\n\n");
            } else {
                sb.append("최신 인바디 기록을 확인했어요!\n\n");
            }
        } else {
            String dateStr = AIChatUtils.formatDateForMessage(date);
            sb.append(dateStr).append(" 인바디 기록을 확인했어요!\n\n");
        }

        if (metric != null && !metric.trim().isEmpty()) {
            // 특정 항목만 조회한 경우
            switch (metric.toUpperCase()) {
                case "BODY_FAT":
                    if (bodyInfo.getBodyFatPercent() != null) {
                        sb.append("📉 체지방률: ").append(String.format("%.1f", bodyInfo.getBodyFatPercent())).append("%\n");
                    }
                    if (bodyInfo.getBodyFatMass() != null) {
                        sb.append("📉 체지방량: ").append(String.format("%.1f", bodyInfo.getBodyFatMass())).append("kg\n");
                    }
                    break;
                case "SKELETAL_MUSCLE":
                    if (bodyInfo.getSkeletalMuscleMass() != null) {
                        sb.append("💪 골격근량: ").append(String.format("%.1f", bodyInfo.getSkeletalMuscleMass())).append("kg\n");
                    }
                    break;
                case "WEIGHT":
                    if (bodyInfo.getWeight() != null) {
                        sb.append("⚖️ 체중: ").append(String.format("%.1f", bodyInfo.getWeight())).append("kg\n");
                    }
                    break;
            }
        } else {
            // 모든 항목 조회한 경우
            if (bodyInfo.getWeight() != null) {
                sb.append("⚖️ 체중: ").append(String.format("%.1f", bodyInfo.getWeight())).append("kg\n");
            }
            if (bodyInfo.getBodyFatPercent() != null) {
                sb.append("📉 체지방률: ").append(String.format("%.1f", bodyInfo.getBodyFatPercent())).append("%\n");
            }
            if (bodyInfo.getSkeletalMuscleMass() != null) {
                sb.append("💪 골격근량: ").append(String.format("%.1f", bodyInfo.getSkeletalMuscleMass())).append("kg\n");
            }
            if (bodyInfo.getBodyFatMass() != null) {
                sb.append("📉 체지방량: ").append(String.format("%.1f", bodyInfo.getBodyFatMass())).append("kg\n");
            }
        }

        return sb.toString();
    }

    /**
     * metric에 따라 인바디 정보 필터링
     */
    private MemberInfoBodyResponseDTO filterByMetric(MemberInfoBodyResponseDTO bodyInfo, String metric) {
        if (metric == null || metric.trim().isEmpty() || bodyInfo == null) {
            return bodyInfo;
        }

        MemberInfoBodyResponseDTO filteredDto = MemberInfoBodyResponseDTO.builder()
            .id(bodyInfo.getId())
            .memberId(bodyInfo.getMemberId())
            .measuredTime(bodyInfo.getMeasuredTime())
            .createdAt(bodyInfo.getCreatedAt())
            .updatedAt(bodyInfo.getUpdatedAt())
            .build();

        switch (metric.toUpperCase()) {
            case "BODY_FAT":
                filteredDto.setBodyFatPercent(bodyInfo.getBodyFatPercent());
                filteredDto.setBodyFatMass(bodyInfo.getBodyFatMass());
                break;
            case "SKELETAL_MUSCLE":
                filteredDto.setSkeletalMuscleMass(bodyInfo.getSkeletalMuscleMass());
                break;
            case "WEIGHT":
                filteredDto.setWeight(bodyInfo.getWeight());
                break;
            default:
                // 알 수 없는 metric이면 모든 항목 반환
                return bodyInfo;
        }

        return filteredDto;
    }
}

