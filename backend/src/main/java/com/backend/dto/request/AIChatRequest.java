package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequest {
    /**
     * 사용자 입력 텍스트
     */
    private String text;
    
    /**
     * 첨부된 이미지 파일 (선택적)
     */
    private MultipartFile image;
    
    /**
     * 대화 히스토리 (트리거 키워드 감지 시 사용)
     * 최근 대화 2개 (AI 1개 + 사용자 1개)
     */
    private List<ChatMessage> conversationHistory;
}
