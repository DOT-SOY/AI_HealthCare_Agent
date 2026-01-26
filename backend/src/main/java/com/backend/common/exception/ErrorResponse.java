package com.backend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
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
        private final Object value;
    }
}
