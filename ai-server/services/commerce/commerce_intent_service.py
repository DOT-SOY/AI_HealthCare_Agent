"""
Commerce 의도 분류 및 Slot 추출 서비스
"""
from typing import Dict, Any
from services.ai_service import call_ai_json
from prompts.commerce.commerce_intent import SYSTEM_PROMPT


def extract_commerce_intent_and_slots(text: str) -> Dict[str, Any]:
    """
    Commerce 의도 및 Slot 추출

    Args:
        text: 사용자 발화

    Returns:
        intent, goal, product_category, budget, avoid, needs_personalization 등
    """
    try:
        result = call_ai_json(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=text,
            temperature=0.0
        )

        kw = result.get("keyword")
        vo = result.get("variant_option")
        address_mode = result.get("address_mode")
        pending_action = result.get("pending_action")
        recipient_name = result.get("recipient_name")
        needs_personalization = result.get("needs_personalization")
        return {
            "intent": result.get("intent", "PRODUCT_RECOMMEND"),
            "goal": result.get("goal", "ALL"),
            "product_category": result.get("product_category", "ALL"),
            "budget": result.get("budget"),
            "avoid": result.get("avoid", []),
            "keyword": kw.strip() if isinstance(kw, str) and kw else None,
            "variant_option": vo.strip() if isinstance(vo, str) and vo else None,
            "address_mode": address_mode.strip().upper() if isinstance(address_mode, str) and address_mode else None,
            "pending_action": pending_action.strip().upper() if isinstance(pending_action, str) and pending_action else None,
            "recipient_name": recipient_name.strip() if isinstance(recipient_name, str) and recipient_name else None,
            "needs_personalization": bool(needs_personalization) if isinstance(needs_personalization, bool) else False,
        }
    except Exception as e:
        print(f"Commerce 의도 분류 실패: {e}")
        return {
            "intent": "PRODUCT_RECOMMEND",
            "goal": "ALL",
            "product_category": "ALL",
            "budget": None,
            "avoid": [],
            "keyword": None,
            "variant_option": None,
            "address_mode": None,
            "pending_action": None,
            "recipient_name": None,
            "needs_personalization": False,
        }
