package com.backend.service.ocr;

import com.backend.client.ocr.ocrInbodyAiClient;
import com.backend.domain.member.Member;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ocrInbodyServiceImpl implements ocrInbodyService {

    private final ocrInbodyAiClient ocrInbodyAiClient;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyDTO extractData(String email, MultipartFile file) {
        // 1. 사용자 식별
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));
        Long memberId = member.getId();

        // 2. AI 서버로 OCR 분석 요청
        Map<String, Object> ocrResult = ocrInbodyAiClient.callAnalyzeApi(file);
        log.info("[OCR Service] 분석 결과: {}", ocrResult);

        // 3. DTO 빌드 (OCR 결과만 사용, 기존 데이터 병합하지 않음)
        // measuredTime 파싱: OCR 결과에서 날짜 추출
        log.info("[OCR Service] OCR 결과에서 measuredTime 추출 시도: {}", ocrResult.get("measuredTime"));
        Instant measuredTime = parseMeasuredDate(ocrResult);
        if (measuredTime == null) {
            log.warn("[OCR Service] measuredTime 파싱 실패, 현재 시간 사용");
            measuredTime = Instant.now(); // 파싱 실패 시 현재 시간 사용
        } else {
            log.info("[OCR Service] measuredTime 파싱 성공: {}", measuredTime);
        }
        
        MemberInfoBodyDTO.MemberInfoBodyDTOBuilder dtoBuilder = MemberInfoBodyDTO.builder()
                .memberId(memberId)
                .measuredTime(measuredTime);

        // 4. OCR 결과만 적용 (기존 데이터 병합하지 않음)
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

    /**
     * OCR 결과에서 measuredTime을 파싱하여 Instant로 변환
     * 형식: "2025.04.11 21:27" 또는 "2025.04.11. 21:27"
     */
    private Instant parseMeasuredDate(Map<String, Object> ocrResult) {
        if (ocrResult == null) return null;
        
        // measuredTime 또는 measuredDate 모두 지원 (GPT가 measuredDate로 반환할 수 있음)
        Object measuredTimeObj = ocrResult.get("measuredTime");
        if (measuredTimeObj == null) {
            measuredTimeObj = ocrResult.get("measuredDate");
        }
        if (measuredTimeObj == null) return null;
        
        String measuredTimeStr = measuredTimeObj.toString().trim();
        if (measuredTimeStr.isEmpty()) return null;
        
        try {
            // "2025.04.11 21:27" 또는 "2025.04.11. 21:27" 형식 파싱
            // 점(.)을 하이픈(-)으로 변환하고 공백으로 날짜/시간 분리
            String normalized = measuredTimeStr.replace(".", "-");
            // 마지막 점 제거 (예: "2025-04-11- 21:27" -> "2025-04-11 21:27")
            normalized = normalized.replaceAll("-\\s+", " ");
            String[] parts = normalized.split("\\s+");
            
            if (parts.length >= 1) {
                // 날짜 부분만 파싱 (시간은 무시)
                String datePart = parts[0];
                // 마지막 하이픈 제거 (예: "2025-04-11-" -> "2025-04-11")
                if (datePart.endsWith("-")) {
                    datePart = datePart.substring(0, datePart.length() - 1);
                }
                // "2025-04-11" 형식으로 변환
                if (datePart.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                    LocalDateTime dateTime = LocalDateTime.parse(datePart + "T00:00:00", 
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant();
                }
            }
        } catch (Exception e) {
            log.warn("[OCR Service] measuredTime 파싱 실패: {}", measuredTimeStr, e);
        }
        
        return null;
    }
}
