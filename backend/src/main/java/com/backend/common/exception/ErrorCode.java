package com.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통
    INTERNAL_SERVER_ERROR("COMMON_001", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_VALUE("COMMON_002", "입력값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    
    // 상품 관련
    PRODUCT_NOT_FOUND("PRODUCT_001", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS("PRODUCT_002", "이미 존재하는 상품입니다.", HttpStatus.CONFLICT),
    INVALID_PRODUCT_STATUS("PRODUCT_003", "유효하지 않은 상품 상태입니다.", HttpStatus.BAD_REQUEST),
    
    // 카테고리 관련
    CATEGORY_NOT_FOUND("CATEGORY_001", "카테고리를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    
    // 권한 관련 (추후 확장)
    // UNAUTHORIZED("AUTH_001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    // FORBIDDEN("AUTH_002", "권한이 없습니다.", HttpStatus.FORBIDDEN),
    
    // 재고 관련
    INVALID_STOCK_QUANTITY("STOCK_001", "재고 수량이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_STOCK("STOCK_002", "재고가 부족합니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
