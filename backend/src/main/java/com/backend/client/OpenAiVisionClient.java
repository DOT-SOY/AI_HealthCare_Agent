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
            + "Extract every number you see next to these labels (Korean or English): "
            + "체중/weight(kg), 키/height(cm), 골격근량/skeletalMuscleMass(kg), 체지방률/bodyFatPercent(%), "
            + "체수분/bodyWater(L), 단백질/protein(kg), 무기질/minerals(kg), 체지방량/bodyFatMass(kg). "
            + "If the image has '검사일시' (inspection date/time), use that date for measurementDate and return it as YYYY-MM-DD (e.g. 2025.01.30. 14:28 -> \"2025-01-30\"). If no 검사일시 or 측정일/검사일, omit or null. "
            + "Return ONLY a single JSON object, no markdown, no explanation. Use null for any value that is missing, blank, or shown as dash. "
            + "Example: {\"weight\":72.4,\"height\":173,\"skeletalMuscleMass\":32.1,\"bodyFatPercent\":18.8,\"bodyWater\":42.0,\"protein\":10.5,\"minerals\":3.2,\"bodyFatMass\":13.6,\"measurementDate\":\"2025-01-30\"}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

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
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
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
            OcrParsedBodyDTO parsed = OcrParsedBodyDTO.builder()
                    .weight(asDoubleOrNull(json.path("weight")))
                    .height(asDoubleOrNull(json.path("height")))
                    .skeletalMuscleMass(asDoubleOrNull(json.path("skeletalMuscleMass")))
                    .bodyFatPercent(asDoubleOrNull(json.path("bodyFatPercent")))
                    .bodyWater(asDoubleOrNull(json.path("bodyWater")))
                    .protein(asDoubleOrNull(json.path("protein")))
                    .minerals(asDoubleOrNull(json.path("minerals")))
                    .bodyFatMass(asDoubleOrNull(json.path("bodyFatMass")))
                    .measurementDate(measurementDate)
                    .build();

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
     * OpenAI API 키가 설정되어 있는지 여부
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
