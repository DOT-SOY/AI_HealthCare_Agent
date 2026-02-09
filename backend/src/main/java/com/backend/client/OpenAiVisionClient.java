package com.backend.client;

import com.backend.dto.ocr.OcrParsedBodyDTO;
import com.backend.dto.ocr.OcrResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI Vision API를 사용해 이미지에서 텍스트를 추출합니다.
 * .env의 OPENAI_API_KEY, OPENAI_MODEL을 사용합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiVisionClient {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OCR_PROMPT =
            "You are reading an InBody or body composition report image (인바디/체성분 검사 결과). "
            + "Extract numbers for: 체중/weight(kg), 키/height(cm), 골격근량/skeletalMuscleMass(kg), 체지방률/bodyFatPercent(%), "
            + "체수분/bodyWater(L), 단백질/protein(kg), 무기질/minerals(kg), 체지방량/bodyFatMass(kg). "
            + "For 적정체중/targetWeight, 체중조절/weightControl, 지방조절/fatControl, 근육조절/muscleControl: use ONLY the four values in the 'Weight Control' (체중조절) box/table. "
            + "In that box the order is always: Row 1 적정체중 (targetWeight), Row 2 체중조절 (weightControl), Row 3 지방조절 (fatControl), Row 4 근육조절 (muscleControl). Match each label to the number in the SAME ROW only; do not mix rows. "
            + "fatControl (지방조절) must be zero or NEGATIVE only (e.g. -2.9, -2.7, 0). If the number next to 지방조절 looks positive and large (e.g. 12.8, 13.3), it is WRONG — that is 체지방량 or another metric. For 지방조절 use only the small negative number or zero in that row, or null if unclear. "
            + "Never set fatControl to a positive number greater than 1. Never use 체지방량 (bodyFatMass, often 12–15 kg) as fatControl. "
            + "weightControl (체중조절) is the DIFFERENCE (current weight minus target weight), so it is a SMALL number (usually between -30 and +30 kg), often negative (e.g. -2.7). Do NOT use the current body weight (체중, often 60–100 kg) as weightControl. The value next to 체중조절 in the Weight Control box is typically small and may have a minus sign. Preserve minus signs for weightControl and fatControl when present. "
            + "If the image has '검사일시' (inspection date/time), use that date for measurementDate as YYYY-MM-DD (e.g. 2025.01.30. 14:28 -> \"2025-01-30\"). If no 검사일시 or 측정일/검사일, omit or null. "
            + "Return ONLY a single JSON object, no markdown, no explanation. Use null for any value that is missing, blank, or shown as dash. "
            + "Example (weightControl=fatControl=small difference, not body weight): {\"weight\":72.9,\"height\":173,\"targetWeight\":68.5,\"weightControl\":-2.7,\"fatControl\":-2.7,\"muscleControl\":0,\"measurementDate\":\"2025-04-11\"}";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String PROTEIN_REGION_PROMPT =
            "이 이미지는 인바디 체성분분석 표의 단백질(kg) 측정치 영역입니다. 이 영역에 있는 숫자 하나만 추출하세요. 소수점 있으면 포함 (예: 11.9). Return ONLY the number, no JSON, no explanation.";
    private static final String MINERALS_REGION_PROMPT =
            "이 이미지는 인바디 체성분분석 표의 무기질(kg) 측정치 영역입니다. 이 영역에 있는 숫자 하나만 추출하세요. 소수점 있으면 포함 (예: 3.81). Return ONLY the number, no JSON, no explanation.";

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${inbody.region.protein.enabled:false}")
    private boolean proteinRegionEnabled;
    @Value("${inbody.region.protein.x:0.14}")
    private double proteinX;
    @Value("${inbody.region.protein.y:0.17}")
    private double proteinY;
    @Value("${inbody.region.protein.w:0.32}")
    private double proteinW;
    @Value("${inbody.region.protein.h:0.055}")
    private double proteinH;
    @Value("${inbody.region.minerals.enabled:false}")
    private boolean mineralsRegionEnabled;
    @Value("${inbody.region.minerals.x:0.14}")
    private double mineralsX;
    @Value("${inbody.region.minerals.y:0.235}")
    private double mineralsY;
    @Value("${inbody.region.minerals.w:0.32}")
    private double mineralsW;
    @Value("${inbody.region.minerals.h:0.055}")
    private double mineralsH;

    /**
     * OpenAI Vision API로 이미지에서 텍스트를 추출합니다.
     * apiKey가 비어 있으면 null을 반환합니다.
     */
    public OcrResponseDTO extractText(MultipartFile file) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("OPENAI_API_KEY가 없어 OpenAI Vision OCR을 건너뜁니다.");
            return null;
        }

        try {
            byte[] fullImageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(fullImageBytes);
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            Map<String, Object> imageContent = Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl)
            );
            Map<String, Object> textContent = Map.of(
                    "type", "text",
                    "text", OCR_PROMPT
            );
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", List.of(textContent, imageContent)
            );
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(message),
                    "max_tokens", 512
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_CHAT_URL, request, String.class);
            String body = response.getBody();

            if (body == null || body.isEmpty()) {
                log.warn("OpenAI Vision 응답 본문이 비어 있습니다.");
                return OcrResponseDTO.builder().language("ko").build();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                return OcrResponseDTO.builder().language("ko").build();
            }
            String content = choices.get(0).path("message").path("content").asText("").trim();
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }
            content = content.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]"); // trailing commas

            JsonNode json = objectMapper.readTree(content);
            String measurementDate = null;
            if (json.has("measurementDate") && json.get("measurementDate").isTextual()) {
                String s = json.get("measurementDate").asText().trim();
                if (!s.isEmpty()) measurementDate = normalizeMeasurementDate(s);
            }
            // 지방조절은 0 이하만 유효; 양수 1 초과면 체지방량 등 오인으로 무시
            Double fatControl = asDoubleOrNull(json.path("fatControl"));
            if (fatControl != null && fatControl > 1.0) {
                log.warn("OCR fatControl {} rejected (지방조절 must be ≤0), discarding", fatControl);
                fatControl = null;
            }
            // 체중조절은 차이값(보통 ±30 이내); 70.2처럼 체중(kg)이 들어간 경우 제거
            Double weight = asDoubleOrNull(json.path("weight"));
            Double weightControl = asDoubleOrNull(json.path("weightControl"));
            if (weightControl != null) {
                if (Math.abs(weightControl) > 30) {
                    log.warn("OCR weightControl {} rejected (체중조절 should be small difference), discarding", weightControl);
                    weightControl = null;
                } else if (weight != null && weightControl > 0 && Math.abs(weightControl - weight) < 5) {
                    log.warn("OCR weightControl {} likely confused with weight {}, discarding", weightControl, weight);
                    weightControl = null;
                }
            }
            OcrParsedBodyDTO parsed = OcrParsedBodyDTO.builder()
                    .weight(weight)
                    .height(asDoubleOrNull(json.path("height")))
                    .skeletalMuscleMass(asDoubleOrNull(json.path("skeletalMuscleMass")))
                    .bodyFatPercent(asDoubleOrNull(json.path("bodyFatPercent")))
                    .bodyWater(asDoubleOrNull(json.path("bodyWater")))
                    .protein(asDoubleOrNull(json.path("protein")))
                    .minerals(asDoubleOrNull(json.path("minerals")))
                    .bodyFatMass(asDoubleOrNull(json.path("bodyFatMass")))
                    .targetWeight(asDoubleOrNull(json.path("targetWeight")))
                    .weightControl(weightControl)
                    .fatControl(fatControl)
                    .muscleControl(asDoubleOrNull(json.path("muscleControl")))
                    .measurementDate(measurementDate)
                    .build();

            // 영역 기반 보정: 파란 박스(단백질), 빨간 박스(무기질) 영역만 크롭 후 Vision으로 재추출
            Double regionProtein = null;
            Double regionMinerals = null;
            if (proteinRegionEnabled && fullImageBytes != null && fullImageBytes.length > 0) {
                try {
                    byte[] cropped = cropImageByRatio(fullImageBytes, proteinX, proteinY, proteinW, proteinH);
                    if (cropped != null) regionProtein = extractSingleNumberFromImage(cropped, PROTEIN_REGION_PROMPT);
                } catch (Exception e) {
                    log.warn("영역 기반 단백질 추출 실패: {}", e.getMessage());
                }
            }
            if (mineralsRegionEnabled && fullImageBytes != null && fullImageBytes.length > 0) {
                try {
                    byte[] cropped = cropImageByRatio(fullImageBytes, mineralsX, mineralsY, mineralsW, mineralsH);
                    if (cropped != null) regionMinerals = extractSingleNumberFromImage(cropped, MINERALS_REGION_PROMPT);
                } catch (Exception e) {
                    log.warn("영역 기반 무기질 추출 실패: {}", e.getMessage());
                }
            }
            if (regionProtein != null || regionMinerals != null) {
                parsed = OcrParsedBodyDTO.builder()
                        .weight(parsed.getWeight())
                        .height(parsed.getHeight())
                        .skeletalMuscleMass(parsed.getSkeletalMuscleMass())
                        .bodyFatPercent(parsed.getBodyFatPercent())
                        .bodyWater(parsed.getBodyWater())
                        .protein(regionProtein != null ? regionProtein : parsed.getProtein())
                        .minerals(regionMinerals != null ? regionMinerals : parsed.getMinerals())
                        .bodyFatMass(parsed.getBodyFatMass())
                        .targetWeight(parsed.getTargetWeight())
                        .weightControl(parsed.getWeightControl())
                        .fatControl(parsed.getFatControl())
                        .muscleControl(parsed.getMuscleControl())
                        .measurementDate(parsed.getMeasurementDate())
                        .build();
            }

            return OcrResponseDTO.builder()
                    .parsed(parsed)
                    .language("ko")
                    .build();
        } catch (Exception e) {
            log.error("OpenAI Vision OCR 실패: {}", e.getMessage(), e);
            if (e.getCause() != null) {
                log.debug("OCR 응답 원문 파싱 실패 시 content 로그로 확인 가능");
            }
            throw new RuntimeException("OpenAI OCR 실패: " + e.getMessage(), e);
        }
    }

    private static final Pattern DATE_DOT_OR_DASH = Pattern.compile("(\\d{4})[.\\-](\\d{1,2})[.\\-](\\d{1,2})");

    /** 검사일시 등에서 오는 날짜 문자열을 YYYY-MM-DD로 정규화 (2025.01.30. 14:28 또는 2025-01-30 등) */
    private static String normalizeMeasurementDate(String s) {
        if (s == null || s.isEmpty()) return null;
        s = s.trim();
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return s;
        Matcher m = DATE_DOT_OR_DASH.matcher(s);
        if (m.find()) {
            String y = m.group(1);
            String mm = m.group(2).length() == 1 ? "0" + m.group(2) : m.group(2);
            String dd = m.group(3).length() == 1 ? "0" + m.group(3) : m.group(3);
            return y + "-" + mm + "-" + dd;
        }
        return null;
    }

    /** JSON 노드에서 숫자 추출 (number/문자열 숫자/null 지원) */
    private static Double asDoubleOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isNumber()) return node.asDouble();
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty() || "-".equals(s)) return null;
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 이미지를 비율(0~1)로 지정한 영역만 잘라서 PNG 바이트로 반환.
     */
    private byte[] cropImageByRatio(byte[] imageBytes, double xRatio, double yRatio, double wRatio, double hRatio) {
        try (InputStream in = new ByteArrayInputStream(imageBytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            int w = img.getWidth();
            int h = img.getHeight();
            int x = (int) Math.round(xRatio * w);
            int y = (int) Math.round(yRatio * h);
            int cw = (int) Math.round(wRatio * w);
            int ch = (int) Math.round(hRatio * h);
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x + cw > w) cw = w - x;
            if (y + ch > h) ch = h - y;
            if (cw <= 0 || ch <= 0) return null;
            BufferedImage sub = img.getSubimage(x, y, cw, ch);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(sub, "png", out)) return null;
            return out.toByteArray();
        } catch (Exception e) {
            log.debug("이미지 크롭 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 크롭된 이미지에서 Vision으로 숫자 하나만 추출.
     */
    private Double extractSingleNumberFromImage(byte[] imageBytes, String prompt) {
        if (imageBytes == null || imageBytes.length == 0) return null;
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/png;base64," + base64Image;
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl)
        );
        Map<String, Object> textContent = Map.of("type", "text", "text", prompt);
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
        );
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(message),
                "max_tokens", 64
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    OPENAI_CHAT_URL, new HttpEntity<>(requestBody, headers), String.class);
            String body = response.getBody();
            if (body == null || body.isEmpty()) return null;
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            // 숫자만 추출 (소수점, 마이너스 허용)
            Matcher m = Pattern.compile("-?\\d+\\.?\\d*").matcher(content);
            if (m.find()) {
                return Double.parseDouble(m.group());
            }
            return null;
        } catch (Exception e) {
            log.debug("영역 Vision 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * OpenAI API 키가 설정되어 있는지 여부
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
