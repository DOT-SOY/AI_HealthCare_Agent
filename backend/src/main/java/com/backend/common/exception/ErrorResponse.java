package com.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    /** 에러 코드 (프론트 code 키로 읽을 수 있도록 동일 값 보관) */
    private final String code;
    private final String error;
    private final String message;
    private final List<FieldError> details;
    private final String timestamp;
    private final String path;

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
