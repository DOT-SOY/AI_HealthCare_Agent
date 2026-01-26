package com.backend.common.exception;

import lombok.Getter;

/**
 * JWT 인증/보안 관련 예외를 표현하는 커스텀 예외
 * 
 * <p>사용 예시:
 * <pre>{@code
 * // 기본 메시지
 * throw new JWTException(ErrorCode.JWT_EXPIRED);
 * 
 * // 동적 값 포함 (메시지 포맷팅)
 * throw new JWTException(ErrorCode.JWT_INVALID, tokenId);
 * }</pre>
 */

@Getter
public class JWTException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] args;

    public JWTException(ErrorCode errorCode, Object... args) {
        super(errorCode.getFormattedMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public JWTException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getFormattedMessage(args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 포맷팅된 메시지를 반환
     * 
     * @return 포맷팅된 에러 메시지
     */
    public String getFormattedMessage() {
        return getMessage(); // 이미 생성자에서 포맷팅되어 있음
    }
}

