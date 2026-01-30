package com.backend.service.ocr;

import com.backend.client.OpenAiVisionClient;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.dto.ocr.OcrResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrServiceImpl implements OcrService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp"
    );

    private final OpenAiVisionClient openAiVisionClient;

    @Override
    public OcrResponseDTO extractText(MultipartFile file) {
        validateFile(file);

        if (!openAiVisionClient.isAvailable()) {
            log.warn("OCR 요청 실패: OPENAI_API_KEY가 설정되지 않았습니다.");
            throw new BusinessException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }

        try {
            OcrResponseDTO response = openAiVisionClient.extractText(file);
            if (response == null) {
                throw new BusinessException(ErrorCode.OCR_EXTRACT_FAILED);
            }
            log.info("OCR 완료 (OpenAI Vision)");
            return normalizeResponse(response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR 추출 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OCR_SERVICE_UNAVAILABLE);
        }
    }

    private static OcrResponseDTO normalizeResponse(OcrResponseDTO response) {
        if (response.getText() == null) {
            return OcrResponseDTO.builder()
                    .text("")
                    .language(response.getLanguage())
                    .confidence(response.getConfidence())
                    .build();
        }
        return response;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            long maxMB = MAX_FILE_SIZE / (1024 * 1024);
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, maxMB);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE, String.join(", ", ALLOWED_CONTENT_TYPES));
        }
    }
}
