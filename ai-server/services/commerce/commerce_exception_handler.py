"""Commerce 예외 → 사용자 메시지."""
from typing import Dict, Any
import httpx


def handle_exception(error: Exception, context: str = "") -> Dict[str, Any]:
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
    messages = {
        "NO_PRODUCTS_FOUND": "조건에 맞는 상품을 찾지 못했습니다. 다른 조건으로 검색해볼까요?",
        "CONDITION_NO_MATCH": "요청하신 조건(예: 무릎 보호대, 손목 밴드)에 맞는 상품이 없어요. 다른 키워드로 검색하시거나, 참고용 추천만 받아보실 수 있어요.",
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


# 상품 없음 → 일반 챗 핸드오프용 시스템 프롬프트
HANDOFF_TO_GENERAL_SYSTEM_PROMPT = """당신은 친근한 운동·건강 AI 코치입니다.
사용자가 "뭐 사야 해?", "뭘 사야 할까"처럼 구매 조언을 구했는데, 우리 쇼핑에는 요청하신 조건에 맞는 상품이 없습니다.
다음 규칙으로 답변하세요:
1. 사용자 상황(예: 등운동 시 손이 아픔, 무릎이 아픔)에 공감하고, 일반적인 조언을 2~4문장으로 친절하게 전달하세요.
2. 예: 손목/무릎 보호 시 손목 밴드·리프팅 스트랩·무릎 보호대 등 대안을 언급하고, 우리 쇼핑에는 해당 상품이 없어 다른 구매처(온라인·스포츠용품점)를 찾아보시라고 권해주세요.
3. 의료적 효능을 단정하지 말고, "도움이 될 수 있어요" 수준으로만 표현하세요.
4. 자연스러운 구어체로, 짧고 따뜻하게 답하세요."""
