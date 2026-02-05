package com.backend.dto.shop.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewImageResponse {

    private UUID uuid;
    private String url;
    private String filePath;
}
