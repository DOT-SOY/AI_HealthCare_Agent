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
    /** 중복 이메일 가입 시 사용 (프론트 code === "DELETED_ACCOUNT" 로 처리) */
    MEMBER_DUPLICATE_EMAIL("DELETED_ACCOUNT", "이미 존재하는 이메일입니다.", HttpStatus.BAD_REQUEST),
    
    // ========== 쇼핑몰 ==========
    SHOP_PRODUCT_NOT_FOUND("SHOP_PRODUCT_001", "상품을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_PRODUCT_ALREADY_EXISTS("SHOP_PRODUCT_002", "이미 존재하는 상품입니다.", HttpStatus.CONFLICT),
    SHOP_PRODUCT_INVALID_STATUS("SHOP_PRODUCT_003", "유효하지 않은 상품 상태입니다.", HttpStatus.BAD_REQUEST),
    SHOP_CATEGORY_NOT_FOUND("SHOP_CATEGORY_001", "카테고리를 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_STOCK_INVALID_QUANTITY("SHOP_STOCK_001", "재고 수량이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    SHOP_STOCK_INSUFFICIENT("SHOP_STOCK_002", "재고가 부족합니다. (요청: %s, 현재: %s)", HttpStatus.BAD_REQUEST),
    SHOP_VARIANT_NOT_FOUND("SHOP_VARIANT_001", "상품 변형을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_VARIANT_OUT_OF_STOCK("SHOP_VARIANT_002", "재고가 부족합니다. (요청: %s, 현재: %s)", HttpStatus.BAD_REQUEST),
    SHOP_VARIANT_INACTIVE("SHOP_VARIANT_003", "판매 중지된 상품 변형입니다. (ID: %s)", HttpStatus.BAD_REQUEST),
    SHOP_CART_NOT_FOUND("SHOP_CART_001", "장바구니를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SHOP_CART_ITEM_NOT_FOUND("SHOP_CART_ITEM_001", "장바구니 아이템을 찾을 수 없습니다. (ID: %s)", HttpStatus.NOT_FOUND),
    SHOP_CART_ACCESS_DENIED("SHOP_CART_002", "장바구니에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    SHOP_CART_ITEM_ACCESS_DENIED("SHOP_CART_ITEM_002", "장바구니 아이템에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    SHOP_CART_ITEM_INVALID_QUANTITY("SHOP_CART_ITEM_003", "수량은 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    
    // ========== 파일 업로드 ==========
    FILE_EMPTY("FILE_001", "업로드할 파일이 없습니다.", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE("FILE_002", "파일 크기가 너무 큽니다. (최대: %s MB)", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_INVALID_TYPE("FILE_003", "지원하지 않는 파일 타입입니다. (허용: %s)", HttpStatus.BAD_REQUEST),
    FILE_INVALID_DIRECTORY("FILE_004", "유효하지 않은 디렉토리입니다.", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("FILE_005", "파일 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    
    // ========== JWT 인증/보안 ==========
    JWT_MALFORMED("JWT_001", "잘못된 JWT 형식입니다.", HttpStatus.UNAUTHORIZED),
    JWT_EXPIRED("JWT_002", "만료된 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED),
    JWT_INVALID("JWT_003", "유효하지 않은 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED),
    JWT_INVALID_TOKEN_TYPE("JWT_004", "유효하지 않은 토큰 타입입니다.", HttpStatus.UNAUTHORIZED),
    JWT_ERROR("JWT_005", "JWT 처리 중 오류가 발생했습니다.", HttpStatus.UNAUTHORIZED),
    JWT_INVALID_REFRESH_CLAIMS("JWT_006", "유효하지 않은 Refresh Token 클레임입니다.", HttpStatus.UNAUTHORIZED),
    JWT_REFRESH_BINDING_MISMATCH("JWT_007", "Access Token과 Refresh Token의 주체가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    JWT_UNKNOWN_REFRESH("JWT_008", "알 수 없는 Refresh Token입니다.", HttpStatus.UNAUTHORIZED),
    JWT_REFRESH_REPLAY_DETECTED("JWT_009", "Refresh Token 재사용이 감지되었습니다. (Replay 공격)", HttpStatus.UNAUTHORIZED),
    JWT_REFRESH_TAMPERED("JWT_010", "변조된 Refresh Token이 감지되었습니다.", HttpStatus.UNAUTHORIZED),
    JWT_REFRESH_DEVICE_MISMATCH("JWT_011", "Refresh Token의 기기 정보가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    JWT_REFRESH_IP_MISMATCH("JWT_012", "Refresh Token의 IP 주소가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED);
    
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
