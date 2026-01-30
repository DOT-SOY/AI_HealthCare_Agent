package com.backend.dto.shop.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReplyCreateRequest {

    @NotBlank(message = "대댓글 내용은 필수입니다")
    @Size(max = 2000, message = "대댓글 내용은 2000자 이하여야 합니다")
    private String content;
}
