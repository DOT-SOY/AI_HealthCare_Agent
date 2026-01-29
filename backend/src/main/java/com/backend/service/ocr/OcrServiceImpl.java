package com.backend.service.ocr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrServiceImpl implements OcrService {

    @Value("${openai.api.key}")
    private String openAiKey;

    @Override
    public Map<String, Double> analyzeInbodyImage(MultipartFile file) {
        try {
            String url = "https://api.openai.com/v1/chat/completions";

            // 1. 이미지를 Base64로 인코딩
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            // 2. 프롬프트 (GPT에게 시킬 명령)
            String prompt = "이 이미지는 인바디(체성분 분석) 결과지야. 여기서 다음 항목의 수치를 찾아서 JSON 형식으로만 답해줘. " +
                    "단위(kg, %, cm 등)는 빼고 숫자만 적어줘. 만약 값이 없으면 null로 해줘.\n" +
                    "JSON Key 목록:\n" +
                    "- height (키/신장)\n" +
                    "- weight (체중)\n" +
                    "- skeletalMuscleMass (골격근량)\n" +
                    "- bodyFatPercent (체지방률)\n" +
                    "- bodyWater (체수분)\n" +
                    "- protein (단백질)\n" +
                    "- minerals (무기질)\n" +
                    "- bodyFatMass (체지방량)";

            // 3. 요청 바디 구성 (GPT-4o Vision 사용)
            Map<String, Object> imageUrlMap = Map.of("url", "data:image/jpeg;base64," + base64Image);
            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrlMap);
            Map<String, Object> textContent = Map.of("type", "text", "text", prompt);

            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", List.of(textContent, imageContent)
            );

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o", // Vision 기능이 있는 최신 모델
                    "messages", List.of(message),
                    "max_tokens", 500
            );

            // 4. API 전송
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            // 응답 받기
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            // 5. 응답 파싱
            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> messageContent = (Map<String, Object>) firstChoice.get("message");
            String contentString = (String) messageContent.get("content");

            // 마크다운 코드블럭(```json ... ```) 제거
            contentString = contentString.replace("```json", "").replace("```", "").trim();

            // JSON 문자열을 Map으로 변환
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(contentString, new TypeReference<Map<String, Double>>() {});

        } catch (Exception e) {
            log.error("GPT 분석 실패", e);
            throw new RuntimeException("이미지 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}