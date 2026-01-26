package com.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * 
 * <p>애플리케이션 전역에서 발생하는 예외를 일관된 형식으로 처리하여 클라이언트에게 반환한다
 * 
 * <p>처리하는 예외 타입:
 * <ul>
 *   <li>BusinessException: 비즈니스 로직 예외</li>
 *   <li>JWTException: JWT 인증/보안 예외</li>
 *   <li>MethodArgumentNotValidException: @Valid 검증 실패 (JSON 요청)</li>
 *   <li>BindException: @Valid 검증 실패 (폼 요청)</li>
 *   <li>IllegalArgumentException: 잘못된 인자 예외</li>
 *   <li>Exception: 기타 예상치 못한 예외</li>
 * </ul>
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.warn("BusinessException: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(e.getErrorCode().getCode())
                .message(e.getFormattedMessage()) // 포맷팅된 메시지 사용
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(errorResponse);
    }

    /**
     * JWT 인증/보안 예외 처리
     */
    @ExceptionHandler(JWTException.class)
    public ResponseEntity<ErrorResponse> handleJWTException(
            JWTException e, HttpServletRequest request) {
        log.warn("JWTException: {} - {}", e.getErrorCode().getCode(), e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(e.getErrorCode().getCode())
                .message(e.getFormattedMessage()) // 포맷팅된 메시지 사용
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(errorResponse);
    }

    /**
     * @Valid 검증 실패 예외 처리 (JSON 요청)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("Validation error: {}", e.getMessage());
        return buildValidationErrorResponse(e.getBindingResult(), request);
    }

    /**
     * @Valid 검증 실패 예외 처리 (폼 요청)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(
            BindException e, HttpServletRequest request) {
        log.warn("BindException: {}", e.getMessage());
        return buildValidationErrorResponse(e.getBindingResult(), request);
    }

    /**
     * 잘못된 인자 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        log.warn("IllegalArgumentException: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorCode.INVALID_INPUT_VALUE.getCode())
                .message(e.getMessage())
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorResponse);
    }

    /**
     * 예상치 못한 예외 처리 (모든 예외의 최종 안전망)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .message("서버 내부 오류가 발생했습니다.")
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    /**
     * 검증 에러 응답 생성 (중복 코드 제거)
     */
    private ResponseEntity<ErrorResponse> buildValidationErrorResponse(
            BindingResult bindingResult, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = bindingResult
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .value(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(ErrorCode.INVALID_INPUT_VALUE.getCode())
                .message("입력값 검증에 실패했습니다.")
                .details(fieldErrors)
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorResponse);
    }
}
