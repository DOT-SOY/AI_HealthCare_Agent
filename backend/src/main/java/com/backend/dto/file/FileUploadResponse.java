package com.backend.dto.file;

import lombok.Builder;
import lombok.Getter;

/**
 * 파일 업로드 응답 DTO
 */
@Getter
@Builder
public class FileUploadResponse {
    private String filePath; // 스토리지 키 (파일 경로)
    private String url; // 접근 가능한 URL
    private Long fileSize; // 파일 크기 (bytes)
    private String contentType; // 파일 타입
}

