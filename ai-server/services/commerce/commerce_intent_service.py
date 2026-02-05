"""Commerce 의도·슬롯 추출."""
from typing import Dict, Any
from services.ai_service import call_ai_json
from prompts.commerce.commerce_intent import SYSTEM_PROMPT
from services.commerce.commerce_rag_service import search_commerce_intent_examples


def extract_commerce_intent_and_slots(text: str) -> Dict[str, Any]:
    try:
        # 1) intent_example RAG에서 유사 발화 예시를 가져와 few-shot 힌트로 사용
        example_block = ""
        try:
            rag_result = search_commerce_intent_examples(text, limit=3)
            docs = (rag_result or {}).get("results") or []
            example_lines = []
            for d in docs:
                content = str(d.get("content") or "").strip()
                if content:
                    example_lines.append(content)
            if example_lines:
                example_block = (
                    "\n\n[예시]\n"
                    + "\n\n".join(example_lines)
                    + "\n\n[지금부터 위 예시 스타일을 따라, 아래 사용자 발화에 대한 JSON만 반환하세요.]"
                )
        except Exception as e:
            print(f"Commerce intent 예시 RAG 조회 실패: {e}")

        system_prompt = SYSTEM_PROMPT + example_block

        # 2) LLM 호출
        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=text,
            temperature=0.0
        )
        if not isinstance(result, dict):
            result = {}

        intent = result.get("intent")
        intent = str(intent).strip() if intent else "PRODUCT_RECOMMEND"
        if intent != "PRODUCT_RECOMMEND":
            intent = "PRODUCT_RECOMMEND"

        goal = result.get("goal")
        goal = str(goal).strip().upper() if goal else "ALL"
        product_category = result.get("product_category")
        product_category = str(product_category).strip().upper() if product_category else "ALL"

        budget_raw = result.get("budget")
        if budget_raw is None:
            budget = None
        elif isinstance(budget_raw, (int, float)) and budget_raw >= 0:
            budget = int(budget_raw) if isinstance(budget_raw, float) and budget_raw == int(budget_raw) else budget_raw
        else:
            budget = None

        avoid_raw = result.get("avoid", [])
        avoid = list(avoid_raw) if isinstance(avoid_raw, list) else []
        avoid = [str(x).strip() for x in avoid if x is not None and str(x).strip()]

        core_kw_raw = result.get("core_keywords", [])
        core_keywords = list(core_kw_raw) if isinstance(core_kw_raw, list) else []
        core_keywords = [str(x).strip() for x in core_keywords if x is not None and str(x).strip()]

        neg_kw_raw = result.get("negative_keywords", [])
        negative_keywords = list(neg_kw_raw) if isinstance(neg_kw_raw, list) else []
        negative_keywords = [str(x).strip() for x in negative_keywords if x is not None and str(x).strip()]

        kw = result.get("keyword")
        keyword = kw.strip() if isinstance(kw, str) and kw and kw.strip() else None
        vo = result.get("variant_option")
        variant_option = vo.strip() if isinstance(vo, str) and vo and vo.strip() else None
        address_mode = result.get("address_mode")
        address_mode = address_mode.strip().upper() if isinstance(address_mode, str) and address_mode and address_mode.strip() else None
        pending_action = result.get("pending_action")
        pending_action = pending_action.strip().upper() if isinstance(pending_action, str) and pending_action and pending_action.strip() else None
        recipient_name = result.get("recipient_name")
        recipient_name = recipient_name.strip() if isinstance(recipient_name, str) and recipient_name and recipient_name.strip() else None
        needs_personalization = bool(result.get("needs_personalization")) if isinstance(result.get("needs_personalization"), bool) else False

        target_body_part = result.get("target_body_part")
        target_body_part = (
            target_body_part.strip().upper()
            if isinstance(target_body_part, str) and target_body_part and target_body_part.strip()
            else None
        )
        product_usage = result.get("product_usage")
        product_usage = (
            product_usage.strip().upper()
            if isinstance(product_usage, str) and product_usage and product_usage.strip()
            else None
        )
        experience_level = result.get("experience_level")
        experience_level = (
            experience_level.strip().upper()
            if isinstance(experience_level, str) and experience_level and experience_level.strip()
            else None
        )

        return {
            "intent": intent,
            "goal": goal,
            "product_category": product_category,
            "budget": budget,
            "avoid": avoid,
            "keyword": keyword,
            "variant_option": variant_option,
            "address_mode": address_mode,
            "pending_action": pending_action,
            "recipient_name": recipient_name,
            "needs_personalization": needs_personalization,
            "target_body_part": target_body_part,
            "product_usage": product_usage,
            "experience_level": experience_level,
            "core_keywords": core_keywords,
            "negative_keywords": negative_keywords,
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
