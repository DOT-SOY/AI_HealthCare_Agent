package com.backend.service.ocr;

import com.backend.client.ocr.ocrInbodyAiClient;
import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ocrInbodyServiceImpl implements ocrInbodyService {

    private final ocrInbodyAiClient ocrInbodyAiClient;
    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyDTO extractData(String email, MultipartFile file) {
        // 1. 사용자 식별
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));
        Long memberId = member.getId();

        // 2. 가장 최근 신체 정보 조회 (병합용)
        MemberInfoBody latest = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId)
                .stream().findFirst().orElse(null);

        // 3. AI 서버로 OCR 분석 요청
        Map<String, Object> ocrResult = ocrInbodyAiClient.callAnalyzeApi(file);
        log.info("[OCR Service] 분석 결과: {}", ocrResult);

        // 4. DTO 빌드 (기존 값 + OCR 값)
        // 저장은 하지 않으므로 Entity를 만들지 않고 DTO를 바로 만듭니다.
        MemberInfoBodyDTO.MemberInfoBodyDTOBuilder dtoBuilder = MemberInfoBodyDTO.builder()
                .memberId(memberId)
                .measuredTime(Instant.now());

        if (latest != null) {
            // 기존 값 복사 (누락 방지)
            dtoBuilder
                    .height(latest.getHeight())
                    .weight(latest.getWeight())
                    .skeletalMuscleMass(latest.getSkeletalMuscleMass())
                    .bodyFatPercent(latest.getBodyFatPercent())
                    .bodyWater(latest.getBodyWater())
                    .protein(latest.getProtein())
                    .minerals(latest.getMinerals())
                    .bodyFatMass(latest.getBodyFatMass())
                    .targetWeight(latest.getTargetWeight())
                    .weightControl(latest.getWeightControl())
                    .fatControl(latest.getFatControl())
                    .muscleControl(latest.getMuscleControl())
                    .exercisePurpose(latest.getExercisePurpose());
        }

        // 5. OCR 결과 덮어쓰기
        applyOcrValues(dtoBuilder, ocrResult);

        return dtoBuilder.build();
    }

    private void applyOcrValues(MemberInfoBodyDTO.MemberInfoBodyDTOBuilder builder, Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;

        java.util.function.Function<String, Double> getDouble = key -> {
            Object val = data.get(key);
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val instanceof String) {
                try { return Double.parseDouble((String) val); } catch (Exception e) {}
            }
            return null;
        };

        // AI-server가 단위 없는 키(weight/protein/minerals...)로 반환할 수 있어 둘 다 지원
        Double w = getDouble.apply("weightKg");
        if (w == null) w = getDouble.apply("weight");
        if (w != null) builder.weight(w);

        Double smm = getDouble.apply("skeletalMuscleMassKg");
        if (smm == null) smm = getDouble.apply("skeletalMuscleMass");
        if (smm != null) builder.skeletalMuscleMass(smm);

        // 컬럼/엔티티는 그대로(bodyFatMass) 두고, 값을 '제지방량' 의미로 사용 (프론트 라벨만 제지방량)
        Double bfm = getDouble.apply("bodyFatMassKg");
        if (bfm == null) bfm = getDouble.apply("bodyFatMass");
        if (bfm != null) builder.bodyFatMass(bfm);

        Double bfp = getDouble.apply("bodyFatPercent");
        if (bfp != null) builder.bodyFatPercent(bfp);

        Double h = getDouble.apply("heightCm");
        if (h == null) h = getDouble.apply("height");
        if (h != null) builder.height(h);
        
        Double bw = getDouble.apply("bodyWaterL");
        if (bw == null) bw = getDouble.apply("bodyWater");
        if (bw != null) builder.bodyWater(bw);
        
        Double pr = getDouble.apply("proteinKg");
        if (pr == null) pr = getDouble.apply("protein");
        if (pr != null) builder.protein(pr);
        
        Double mi = getDouble.apply("mineralsKg");
        if (mi == null) mi = getDouble.apply("minerals");
        if (mi != null) builder.minerals(mi);

        Double tw = getDouble.apply("targetWeight");
        if (tw != null) builder.targetWeight(tw);

        Double wc = getDouble.apply("weightControl");
        if (wc != null) builder.weightControl(wc);

        Double fc = getDouble.apply("fatControl");
        if (fc != null) builder.fatControl(fc);

        Double mc = getDouble.apply("muscleControl");
        if (mc != null) builder.muscleControl(mc);
    }
}
