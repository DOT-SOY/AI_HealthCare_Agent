package com.backend.dto.shop.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 상품 이미지 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponse {
    private UUID uuid;
    private String url; // FileStorageService를 통해 조립된 URL
    private String filePath; // 스토리지 키 (파일 경로) - 프론트엔드에서 이미지 수정 시 필요
    private boolean primaryImage;
}

