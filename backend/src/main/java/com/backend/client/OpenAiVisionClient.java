package com.backend.client;

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

/**
 * OpenAI Vision API를 사용해 이미지에서 텍스트를 추출합니다.
 * .env의 OPENAI_API_KEY, OPENAI_MODEL을 사용합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiVisionClient {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OCR_PROMPT = "Extract all text from this image. Return only the raw extracted text, no explanation. If the image contains no text, return an empty string.";

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
                    "max_tokens", 1024
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_CHAT_URL, request, String.class);
            String body = response.getBody();

            if (body == null || body.isEmpty()) {
                log.warn("OpenAI Vision 응답 본문이 비어 있습니다.");
                return OcrResponseDTO.builder().text("").build();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                return OcrResponseDTO.builder().text("").build();
            }
            String content = choices.get(0).path("message").path("content").asText("").trim();

            return OcrResponseDTO.builder()
                    .text(content)
                    .language("ko")
                    .build();
        } catch (Exception e) {
            log.error("OpenAI Vision OCR 실패: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI OCR 실패: " + e.getMessage(), e);
        }
    }

    /**
     * OpenAI API 키가 설정되어 있는지 여부
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
