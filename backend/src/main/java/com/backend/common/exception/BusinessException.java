package com.backend.common.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 발생하는 예외를 표현하는 커스텀 예외
 * 
 * <p>사용 예시:
 * <pre>{@code
 * // 기본 메시지
 * throw new BusinessException(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS);
 * 
 * // 동적 값 포함 (메시지 포맷팅)
 * throw new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId);
 * throw new BusinessException(ErrorCode.SHOP_STOCK_INSUFFICIENT, requestedQty, currentQty);
 * }</pre>
 */

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] args;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getFormattedMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
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
