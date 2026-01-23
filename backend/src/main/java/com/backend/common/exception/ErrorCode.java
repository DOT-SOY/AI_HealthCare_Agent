package com.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전체 에러 코드 정의
 * 
 * <p>에러 코드 네이밍 규칙:
 * <ul>
 *   <li>공통: COMMON_{NUMBER}</li>
 *   <li>모듈: {MODULE}_{DOMAIN}_{NUMBER}</li>
 *   <li>예시: SHOP_PRODUCT_001, ROUTINE_001, MEAL_RECORD_001</li>
 * </ul>
 * 
 * <p>메시지 포맷팅:
 * <ul>
 *   <li>동적 값을 포함하려면 String.format 형식 사용: "상품을 찾을 수 없습니다. (ID: %s)"</li>
 *   <li>BusinessException 생성 시 args로 값 전달: new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId)</li>
 * </ul>
 */

@Getter
public enum ErrorCode {
    // ========== 공통 에러 ==========
    INTERNAL_SERVER_ERROR("COMMON_001", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_VALUE("COMMON_002", "입력값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    MEMBER_NOT_FOUND("COMMON_MEMBER_001", "멤버를 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    
    // ========== 쇼핑몰 ==========
    SHOP_PRODUCT_NOT_FOUND("SHOP_PRODUCT_001", "상품을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_PRODUCT_ALREADY_EXISTS("SHOP_PRODUCT_002", "이미 존재하는 상품입니다.", HttpStatus.CONFLICT),
    SHOP_PRODUCT_INVALID_STATUS("SHOP_PRODUCT_003", "유효하지 않은 상품 상태입니다.", HttpStatus.BAD_REQUEST),
    SHOP_CATEGORY_NOT_FOUND("SHOP_CATEGORY_001", "카테고리를 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_STOCK_INVALID_QUANTITY("SHOP_STOCK_001", "재고 수량이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    SHOP_STOCK_INSUFFICIENT("SHOP_STOCK_002", "재고가 부족합니다. (요청: %s, 현재: %s)", HttpStatus.BAD_REQUEST);
    
    // ========== 루틴 (예시) ==========
    // ROUTINE_NOT_FOUND("ROUTINE_001", "루틴을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    // ROUTINE_ALREADY_EXISTS("ROUTINE_002", "이미 존재하는 루틴입니다.", HttpStatus.CONFLICT),
    
    // ========== 식사 (예시) ==========
    // MEAL_NOT_FOUND("MEAL_001", "식사 기록을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    // MEAL_RECORD_INVALID("MEAL_002", "유효하지 않은 식사 기록입니다.", HttpStatus.BAD_REQUEST),
    
    // ========== 기록 (예시) ==========
    // RECORD_NOT_FOUND("RECORD_001", "기록을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    // RECORD_ALREADY_EXISTS("RECORD_002", "이미 존재하는 기록입니다.", HttpStatus.CONFLICT),
    
    // ========== 권한/인증 모듈 (추후 추가 예정) ==========
    // AUTH_UNAUTHORIZED("AUTH_001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    // AUTH_FORBIDDEN("AUTH_002", "권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    /**
     * 메시지를 포맷팅하여 반환
     * 
     * @param args 포맷팅에 사용할 인자들
     * @return 포맷팅된 메시지
     */
    public String getFormattedMessage(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (Exception e) {
            // 포맷팅 실패 시 원본 메시지 반환
            return message;
        }
    }
}
