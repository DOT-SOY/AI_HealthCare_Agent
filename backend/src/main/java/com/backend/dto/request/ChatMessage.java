package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채팅 메시지 DTO
 * 대화 히스토리를 전송할 때 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

