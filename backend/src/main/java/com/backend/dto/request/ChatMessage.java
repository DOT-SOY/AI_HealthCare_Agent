package com.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채팅 메시지 DTO
 * 대화 히스토리를 전송할 때 사용 (프론트에서 imageUrl 등 추가 필드가 와도 무시)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    /**
     * 메시지 역할 (user 또는 assistant)
     */
    private String role;
    
    /**
     * 메시지 내용
     */
    private String content;
}

