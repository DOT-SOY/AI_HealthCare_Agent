"""
Commerce 예외 처리 및 사용자 메시지 가이드
"""
from typing import Dict, Any
import httpx


def handle_exception(error: Exception, context: str = "") -> Dict[str, Any]:
    """
    예외를 사용자 친화적인 메시지로 변환
    
    Args:
        error: 발생한 예외
        context: 컨텍스트 정보
    
    Returns:
        에러 응답 딕셔너리
    """
    error_type = type(error).__name__
    
    if isinstance(error, httpx.TimeoutException):
        return {
            "message": "요청 시간이 초과되었습니다. 다시 시도해주세요.",
            "error": "TIMEOUT",
            "error_type": error_type
        }
    elif isinstance(error, httpx.HTTPStatusError):
        status_code = error.response.status_code
        if status_code == 404:
            if "product" in context.lower():
                return {
                    "message": "선택하신 상품을 찾을 수 없습니다. 다른 상품을 추천해드릴까요?",
                    "error": "PRODUCT_NOT_FOUND",
                    "error_type": error_type
                }
            elif "address" in context.lower():
                return {
                    "message": "배송지를 찾을 수 없습니다. 배송지를 입력해주세요.",
                    "error": "ADDRESS_NOT_FOUND",
                    "error_type": error_type
                }
            else:
                return {
                    "message": "요청한 리소스를 찾을 수 없습니다.",
                    "error": "NOT_FOUND",
                    "error_type": error_type
                }
        elif status_code == 409:
            return {
                "message": "이미 처리된 요청입니다.",
                "error": "DUPLICATE_REQUEST",
                "error_type": error_type
            }
        elif status_code == 400:
            return {
                "message": "잘못된 요청입니다. 다시 확인해주세요.",
                "error": "BAD_REQUEST",
                "error_type": error_type
            }
        elif status_code == 401 or status_code == 403:
            return {
                "message": "인증이 필요합니다.",
                "error": "AUTH_REQUIRED",
                "error_type": error_type
            }
        elif status_code >= 500:
            return {
                "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                "error": "SERVER_ERROR",
                "error_type": error_type
            }
        else:
            return {
                "message": "요청 처리 중 오류가 발생했습니다.",
                "error": "HTTP_ERROR",
                "error_type": error_type,
                "status_code": status_code
            }
    elif isinstance(error, httpx.RequestError):
        return {
            "message": "네트워크 오류가 발생했습니다. 연결을 확인해주세요.",
            "error": "NETWORK_ERROR",
            "error_type": error_type
        }
    else:
        return {
            "message": "처리 중 오류가 발생했습니다. 다시 시도해주세요.",
            "error": "UNKNOWN_ERROR",
            "error_type": error_type
        }


def get_user_message_for_error(error_code: str, context: Dict[str, Any] = None) -> str:
    """
    에러 코드에 따른 사용자 메시지 반환
    
    Args:
        error_code: 에러 코드
        context: 추가 컨텍스트
    
    Returns:
        사용자 메시지
    """
    messages = {
        "NO_PRODUCTS_FOUND": "조건에 맞는 상품을 찾지 못했습니다. 다른 조건으로 검색해볼까요?",
        "PRODUCT_OUT_OF_STOCK": "선택하신 상품이 품절되었습니다. 다른 상품을 추천해드릴까요?",
        "NO_ADDRESS": "배송지를 입력해주세요.",
        "ADDRESS_NOT_FOUND": "배송지를 찾을 수 없습니다. 배송지를 입력해주세요.",
        "PAYMENT_READY_FAILED": "결제 준비 중 오류가 발생했습니다. 다시 시도해주세요.",
        "CART_ADD_FAILED": "장바구니 담기 중 오류가 발생했습니다. 다시 시도해주세요.",
        "SESSION_EXPIRED": "세션이 만료되었습니다. 다시 시작해주세요.",
        "AUTH_REQUIRED": "인증이 필요합니다.",
        "TIMEOUT": "요청 시간이 초과되었습니다. 다시 시도해주세요.",
        "CONDITION_GENERATION_FAILED": "추천 조건을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.",
        "PROCESSING_ERROR": "처리 중 오류가 발생했습니다. 다시 시도해주세요."
    }
    
    return messages.get(error_code, "오류가 발생했습니다. 다시 시도해주세요.")

