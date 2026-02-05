"""Commerce 추천 오케스트레이션.

AI 추천 ↔ 결제 전이 시 역할 분리 원칙:

1. AI/ai-server 역할 (이 모듈):
   - 사용자 자연어 → 추천 조건(RecommendationCondition) 생성
   - Backend 추천 API 호출 → 추천 상품 목록 수신
   - 사용자에게 추천 결과 제공 및 선택 유도
   - 선택된 상품/variant/수량을 **구조화된 JSON(order draft)**로 전달
   - 단, 최종 결제/주문 생성 결정권은 갖지 않음

2. 프론트엔드 역할:
   - AI가 제안한 추천 리스트/order draft를 UI에 표시
   - 사용자가 실제로 선택한 productId/variantId/quantity를 명시적으로 구성
   - 장바구니/주문 API 호출 시 AI 추천 ID(recommendation_id)를 참고 정보로 전달 가능

3. 백엔드 역할 (서버 권위):
   - 장바구니 담기 시: 상품 존재, variant 활성화, 재고 재검증
   - 주문 생성 시: 상품 상태, 가격, 재고, 회원/쿠폰 정책 재검증
   - AI 추천 결과는 **참고 정보**일 뿐, 최종 주문/결제는 항상 백엔드가 결정
   - 가격은 항상 주문 생성 시점의 서버 기준으로 적용

이 원칙에 따라, 이 모듈은 "추천 제안"까지만 책임지고,
실제 결제 처리는 백엔드의 CartService/OrderService가 서버 권위로 수행합니다.
"""
import os
import re
import time
import uuid
from datetime import datetime
from typing import Dict, Any, Optional, List
import httpx

from services.backend_client import get_user_profile
from schemas.commerce.recommendation_schema import RecommendationCondition

from .commerce_intent_service import extract_commerce_intent_and_slots
from .commerce_recommendation_service import (
    generate_recommendation_condition,
    generate_fallback_conditions,
    generate_slots_and_condition_combined,
)
from .commerce_state_machine import state_machine, CommerceState, SessionData
from .commerce_exception_handler import (
    handle_exception,
    get_user_message_for_error,
    HANDOFF_TO_GENERAL_SYSTEM_PROMPT,
)
from .commerce_logging_service import (
    log_recommendation_generated,
    log_user_rejected,
)
from services.ai_service import call_ai, call_ai_json
from prompts.commerce.commerce_variant_pick import (
    SYSTEM_PROMPT as VARIANT_PICK_SYSTEM_PROMPT,
    build_user_prompt as build_variant_pick_user_prompt,
)

# CONFIRM_* 상태에서 문장형 발화 시 intent 재분류 기준: 이 길이 이상이면 yes/no가 아닌 새 요청으로 간주
_CONFIRM_STATE_SENTENCE_MIN_LEN = 5

# 상위 의도가 이 값이면 커머스 도메인으로 계속 처리; 그 외는 OFF_TOPIC 반환
_COMMERCE_DOMAIN_INTENT = "PRODUCT_RECOMMEND"

BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080")
_http_client: Optional[httpx.Client] = None
HTTP_CLIENT_TIMEOUT = 10.0
USE_COMBINED_CONDITION_LLM = os.getenv("USE_COMBINED_CONDITION_LLM", "false").strip().lower() in (
    "1", "true", "yes"
)


def _get_http_client() -> httpx.Client:
    global _http_client
    if _http_client is None:
        _http_client = httpx.Client(timeout=HTTP_CLIENT_TIMEOUT)
    return _http_client

VARIANT_MATCH_MIN_LEN = 2
PRIORITY_LABELS = {
    "칼로리_낮음": "칼로리 낮은",
    "칼로리_높음": "칼로리 높은",
    "단백질_높음": "단백질 많은",
    "단백질_낮음": "단백질 낮은",
    "당_낮음": "당 낮은",
    "당_높음": "당 높은",
    "식이섬유_높음": "식이섬유 많은",
    "식이섬유_낮음": "식이섬유 낮은",
    "가격_낮음": "가격 부담 적은",
    "가격_높음": "고급",
    "용량_많음": "용량 많은",
    "용량_적음": "용량 적은",
}


def _has_final_consonant(char: str) -> bool:
    if not char or len(char) != 1:
        return False
    code = ord(char)
    if not (0xAC00 <= code <= 0xD7A3):
        return False
    return (code - 0xAC00) % 28 != 0


def _josa(word: str, with_jong: str, without_jong: str) -> str:
    if not word or not word.strip():
        return without_jong
    last_char = word.strip()[-1]
    return with_jong if _has_final_consonant(last_char) else without_jong


_GOAL_VALUES = {"DIET", "MAINTAIN", "BULK_UP", "ALL"}
_CATEGORY_VALUES = {"FOOD", "SUPPLEMENT", "HEALTH_GOODS", "CLOTHING", "ETC", "ALL"}
_ADDRESS_MODE_VALUES = {"DEFAULT", "NEW"}
_PENDING_ACTION_VALUES = {"PAYMENT"}


def _normalize_goal(value: Any) -> str:
    if not value or not str(value).strip():
        return "ALL"
    u = str(value).strip().upper()
    return u if u in _GOAL_VALUES else "ALL"


def _normalize_product_category(value: Any) -> str:
    if not value or not str(value).strip():
        return "ALL"
    u = str(value).strip().upper()
    return u if u in _CATEGORY_VALUES else "ALL"


def _normalize_address_mode(value: Any) -> Optional[str]:
    if value is None or not str(value).strip():
        return None
    u = str(value).strip().upper()
    return u if u in _ADDRESS_MODE_VALUES else None


def _normalize_pending_action(value: Any) -> Optional[str]:
    if value is None or not str(value).strip():
        return None
    u = str(value).strip().upper()
    return u if u in _PENDING_ACTION_VALUES else None


def _is_sentence_like_for_confirm(text: str) -> bool:
    """CONFIRM_* 상태에서 문장형 발화인지 판별. 문장이면 intent 재분류 대상."""
    if not text or not isinstance(text, str):
        return False
    t = text.strip()
    return len(t) >= _CONFIRM_STATE_SENTENCE_MIN_LEN


def _is_new_recommendation_request(text: str, extracted_slots: Dict[str, Any]) -> bool:
    """추출된 슬롯 또는 발화 문맥상 '새 상품 추천 요청'인지 판별."""
    if not text or not isinstance(text, str):
        return False
    tl = text.strip().lower()
    # 명시적 추천/구매 질의 문구
    if any(
        phrase in tl
        for phrase in (
            "추천",
            "뭐 살까",
            "뭘 사야",
            "뭐 사야",
            "어떤 게 좋",
            "어떤게 좋",
            "보여줘",
            "다른 거",
            "다른거",
            "다른 제품",
            "다른 상품",
            "운동기구",
            "보충제",
            "보호대",
        )
    ):
        return True
    goal = (extracted_slots.get("goal") or "").strip().upper()
    cat = (extracted_slots.get("product_category") or "").strip().upper()
    kw = (extracted_slots.get("keyword") or "").strip()
    if goal and goal != "ALL":
        return True
    if cat and cat != "ALL":
        return True
    if kw:
        return True
    return False


def _apply_slot_policy(extracted: Dict[str, Any]) -> Dict[str, Any]:
    """
    intent/slot LLM 결과에 후처리 정책을 적용해 일관된 슬롯으로 보정한다.
    - 보호대/니슬리브/니랩 등은 product_usage=PROTECTOR로 보고 HEALTH_GOODS를 우선한다.
    - target_body_part에 따라 keyword를 보다 구체적인 부위+보호대 형태로 정규화한다.
    """
    slots = dict(extracted or {})
    raw_keyword = slots.get("keyword") or ""
    kw = str(raw_keyword).strip() if isinstance(raw_keyword, str) else ""
    usage = (slots.get("product_usage") or "").strip().upper() if isinstance(slots.get("product_usage"), str) else None
    body = (slots.get("target_body_part") or "").strip().upper() if isinstance(slots.get("target_body_part"), str) else None
    category = (slots.get("product_category") or "").strip().upper() if isinstance(slots.get("product_category"), str) else "ALL"

    # core_keywords / negative_keywords는 리스트 형태로 보정
    core_keywords = slots.get("core_keywords") or []
    if not isinstance(core_keywords, list):
        core_keywords = []
    negative_keywords = slots.get("negative_keywords") or []
    if not isinstance(negative_keywords, list):
        negative_keywords = []

    def _ensure_term(lst, term: str) -> None:
        if not term:
            return
        if term not in lst:
            lst.append(term)

    protector_tokens = ("보호대", "니슬리브", "니슬리브", "니랩", "니 랩", "무릎보호", "무릎 보호")
    is_protector_kw = kw and any(t in kw for t in protector_tokens)

    # 보호대/니슬리브/니랩 등 보호 용품이면 기본적으로 HEALTH_GOODS를 우선한다.
    if usage == "PROTECTOR" or is_protector_kw:
        slots["product_usage"] = "PROTECTOR"
        if category == "ALL" or category == "SUPPLEMENT":
            slots["product_category"] = "HEALTH_GOODS"

        # 부위에 따라 보다 구체적인 keyword로 정규화
        if body in {"KNEE", "LOWER_BODY"}:
            # 무릎/하체 보호대
            if not kw or "무릎" in kw:
                slots["keyword"] = "무릎 보호대"
            # 핵심 키워드에 부위+유형을 모두 포함하도록 보강
            _ensure_term(core_keywords, "무릎")
            _ensure_term(core_keywords, "하체")
            _ensure_term(core_keywords, "보호대")
            # 무릎/하체 보호대 문맥에서는 손/손목 보호 용품은 가급적 피하도록 negative_keywords 보강
            _ensure_term(negative_keywords, "손")
            _ensure_term(negative_keywords, "손목")
        elif body in {"WRIST", "HAND"}:
            # 손/손목 보호/지지 용품
            if not kw or "손목" in kw or "손" in kw:
                # 손목 보호대/밴드/스트랩 중 하나로 통일
                slots["keyword"] = "손목 보호대"
            _ensure_term(core_keywords, "손목")
            _ensure_term(core_keywords, "손")
            _ensure_term(core_keywords, "보호대")
            # 손목 보호대 문맥에서는 무릎/하체 보호대는 피하도록 negative_keywords 보강
            _ensure_term(negative_keywords, "무릎")
            _ensure_term(negative_keywords, "하체")
        elif body in {"BACK"}:
            if not kw or "허리" in kw or "등" in kw:
                slots["keyword"] = "허리 보호대"
            _ensure_term(core_keywords, "허리")
            _ensure_term(core_keywords, "등")
            _ensure_term(core_keywords, "보호대")

    # 보강된 키워드 리스트를 다시 슬롯에 반영
    slots["core_keywords"] = core_keywords
    slots["negative_keywords"] = negative_keywords

    return slots


def _format_address_candidates(addresses: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """저장된 배송지 목록을 참고용 블록 표시용으로 포맷."""
    if not addresses:
        return []
    out = []
    for addr in addresses:
        name = (addr.get("shipToName") or "").strip()
        a1 = (addr.get("shipAddress1") or "").strip()
        a2 = (addr.get("shipAddress2") or "").strip()
        addr_text = f"{a1} {a2}".strip()
        display = f"{name} {addr_text}".strip() if name else addr_text
        out.append({
            "id": addr.get("id"),
            "shipToName": name,
            "display": display or "(주소 없음)",
        })
    return out


def _trim_recommended_products(products: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    out = []
    for p in products:
        variants = p.get("availableVariants") or []
        out.append({
            "productId": p.get("productId"),
            "name": p.get("name", ""),
            "availableVariants": [
                {"variantId": v.get("variantId"), "name": v.get("name", "")}
                for v in variants
            ],
        })
    return out


def build_order_draft(
    recommendation_id: str,
    selected_product_id: int,
    selected_variant_id: Optional[int],
    quantity: int = 1,
    product_name: Optional[str] = None,
    variant_name: Optional[str] = None,
) -> Dict[str, Any]:
    """
    주문 초안 JSON 생성 (참고용).
    
    이 정보는 AI가 제안하는 "주문 초안"일 뿐입니다.
    실제 장바구니/주문 생성 시에는 프론트엔드가 사용자의 최종 선택을 기반으로
    백엔드 API를 호출하고, 백엔드가 모든 정보를 재검증합니다.
    
    Returns:
        {
            "recommendation_id": str,      # 추천 추적용 ID
            "product_id": int,             # 선택된 상품 ID
            "variant_id": int | None,      # 선택된 variant ID
            "quantity": int,               # 수량
            "product_name": str | None,    # 상품명 (참고용)
            "variant_name": str | None,    # 옵션명 (참고용)
            "note": str,                   # 백엔드 재검증 필요 안내
        }
    """
    return {
        "recommendation_id": recommendation_id,
        "product_id": selected_product_id,
        "variant_id": selected_variant_id,
        "quantity": quantity,
        "product_name": product_name,
        "variant_name": variant_name,
        "note": "이 정보는 AI 추천 기반 초안입니다. 실제 주문 시 백엔드에서 상품/가격/재고를 재검증합니다.",
    }


def _build_reason_text(condition: RecommendationCondition) -> str:
    """사용자에게 보여줄 추천 문구. '~걸로 골라봤어요.'에서 끝나도록 한다."""
    goal_labels = {"DIET": "다이어트", "BULK_UP": "벌크업", "MAINTAIN": "체중 유지", "ALL": "선택하신"}
    goal_label = goal_labels.get(condition.goal, "선택하신")
    reason = f"{goal_label}에 맞는 걸로 골라봤어요."
    if condition.priority:
        labels = []
        for p in condition.priority[:3]:
            labels.append(PRIORITY_LABELS.get(p, p.replace("_", " ")))
        if labels:
            reason = f"{goal_label}에 맞춰 {', '.join(labels)} 걸로 골라봤어요."
    return reason


def _generate_handoff_to_general_message(user_text: str, keyword: Optional[str] = None) -> str:
    """원하는 상품이 없을 때 일반 챗으로 넘기기 위한 조언 메시지를 생성한다."""
    try:
        context = f"사용자 질문: {user_text}"
        if keyword and str(keyword).strip():
            context += f"\n요청하신 키워드/조건: {keyword.strip()}"
        reply = call_ai(
            system_prompt=HANDOFF_TO_GENERAL_SYSTEM_PROMPT,
            user_prompt=context,
            temperature=0.6,
        )
        return (reply or "").strip() or get_user_message_for_error("CONDITION_NO_MATCH")
    except Exception as e:
        print(f"[commerce] handoff 메시지 생성 실패: {e}")
        return get_user_message_for_error("CONDITION_NO_MATCH")


def ensure_session_profile(session_id: str, auth_token: Optional[str]) -> None:
    """세션 프로필 캐시가 비어 있을 때만 Backend 프로필 조회 후 세션에 반영."""
    if not auth_token:
        return

    session = state_machine.get_session(session_id)
    if not session:
        return

    if any(
        [
            session.member_gender is not None,
            session.member_height_cm is not None,
            session.member_weight_kg is not None,
        ]
    ):
        return

    profile = get_user_profile(auth_token)
    if not profile:
        return

    goal_type = profile.get("goal", session.goal_type)
    member_gender = profile.get("gender", session.member_gender)
    member_height_cm = (
        profile.get("heightCm")
        if profile.get("heightCm") is not None
        else profile.get("height_cm", session.member_height_cm)
    )
    member_weight_kg = (
        profile.get("weightKg")
        if profile.get("weightKg") is not None
        else profile.get("weight_kg", session.member_weight_kg)
    )
    budget_max = profile.get("budgetMax")
    if budget_max is None:
        budget_max = profile.get("budget_max", session.budget_max)

    profile_avoid = list(getattr(session, "profile_avoid", []) or [])
    allergies = profile.get("allergies") or []
    for a in allergies:
        tag = f"알러지_{a}" if isinstance(a, str) else str(a)
        if tag not in profile_avoid:
            profile_avoid.append(tag)
    for a in profile.get("avoid") or []:
        if a not in profile_avoid:
            profile_avoid.append(a)

    state_machine.update_session(
        session_id,
        goal_type=goal_type,
        member_gender=member_gender,
        member_height_cm=member_height_cm,
        member_weight_kg=member_weight_kg,
        budget_max=budget_max,
        profile_avoid=profile_avoid,
    )


def _parse_numeric_from_name(name: str) -> Optional[float]:
    if not name or not isinstance(name, str):
        return None
    m = re.search(r"(\d+(?:\.\d+)?)\s*(?:kg|g|ml|L|ℓ|oz|lb)?", (name or "").strip(), re.IGNORECASE)
    if m:
        try:
            return float(m.group(1))
        except (ValueError, TypeError):
            pass
    return None


def _pick_variant_id_via_llm(available_variants: List[Dict], option_keyword: str) -> Optional[int]:
    """
    LLM을 사용해 사용자 옵션 키워드와 가장 잘 맞는 variant를 선택한다.
    
    Args:
        available_variants: [{"variantId": int, "name": str, "stockQty": int, "price": float|None}, ...]
        option_keyword: 사용자가 원하는 옵션 문자열 (예: "흰색", "L 사이즈", "20kg", "가벼운 거")
    
    Returns:
        선택된 variantId 또는 None (실패/매칭 없음)
    """
    try:
        # LLM에 전달할 형태로 변환 (재고 수량, 가격 포함)
        variants_for_llm = [
            {
                "variantId": v.get("variantId"),
                "name": v.get("name", ""),
                "stockQty": v.get("stockQty") or 0,
                "price": v.get("price"),  # 있으면 전달, 없으면 None
            }
            for v in available_variants
        ]
        
        user_prompt = build_variant_pick_user_prompt(variants_for_llm, option_keyword)
        
        result = call_ai_json(
            system_prompt=VARIANT_PICK_SYSTEM_PROMPT,
            user_prompt=user_prompt,
            temperature=0.0,
        )
        
        selected_id = result.get("variantId")
        if selected_id is None:
            return None
        
        # 반환된 ID가 실제 목록에 있는지 검증
        valid_ids = {v.get("variantId") for v in available_variants}
        if selected_id in valid_ids:
            return selected_id
        
        print(f"[variant_pick] LLM이 반환한 variantId={selected_id}가 목록에 없음")
        return None
        
    except Exception as e:
        print(f"[variant_pick] LLM 호출 실패, fallback 사용: {e}")
        return None


def _pick_variant_id(available_variants: List[Dict], option_keyword: Optional[str]) -> Optional[int]:
    """
    사용자 옵션 키워드와 가장 잘 맞는 variant를 선택한다.
    
    우선순위:
    1. LLM 기반 선택 (색상/사이즈/무게/가격 등 모든 옵션 처리)
    2. Fallback: 가벼운/무거운 숫자 정렬
    3. Fallback: 문자열 토큰 매칭
    4. Fallback: 첫 번째 variant
    """
    if not available_variants:
        return None
    raw = (option_keyword or "").strip()
    if not raw:
        return available_variants[0].get("variantId")

    def norm(s: str) -> str:
        return (s or "").strip().lower()

    # 1. LLM 기반 옵션 선택 시도 (variant가 2개 이상일 때만)
    if len(available_variants) >= 2:
        llm_result = _pick_variant_id_via_llm(available_variants, raw)
        if llm_result is not None:
            print(f"[variant_pick] LLM 선택 성공: variantId={llm_result}, option_keyword={raw}")
            return llm_result
        print(f"[variant_pick] LLM 선택 실패, fallback 사용: option_keyword={raw}")

    # 2. Fallback: 가벼운/무거운 숫자 정렬 로직
    raw_lower = norm(raw)
    want_light = any(k in raw_lower for k in ("가벼운", "가벼운걸", "가벼운 걸", "제일 가벼운", "낮은", "작은", "최소", "light", "small"))
    want_heavy = any(k in raw_lower for k in ("무거운", "무거운걸", "무거운 걸", "제일 무거운", "높은", "큰", "최대", "heavy", "large", "big"))
    if want_light or want_heavy:
        def sort_key(v: Dict) -> tuple:
            num = _parse_numeric_from_name(str(v.get("name") or ""))
            if num is not None:
                return (0, num)
            return (1, 0.0)
        with_stock = [v for v in available_variants if (v.get("stockQty") or 0) > 0]
        candidates = with_stock if with_stock else available_variants
        sorted_candidates = sorted(candidates, key=sort_key)
        if sorted_candidates:
            idx = 0 if want_light else -1
            return sorted_candidates[idx].get("variantId")

    # 3. Fallback: 문자열 토큰 매칭 로직
    tokens = [t.strip() for t in re.split(r"[\s,]+", raw) if len(t.strip()) >= VARIANT_MATCH_MIN_LEN]
    if not tokens:
        return available_variants[0].get("variantId")

    with_stock = [v for v in available_variants if (v.get("stockQty") or 0) > 0]
    candidates = with_stock if with_stock else available_variants
    for v in candidates:
        name = norm(v.get("name") or "")
        if not name:
            continue
        if any(n in name or name in n for n in [norm(t) for t in tokens]):
            return v.get("variantId")
    
    # 4. Fallback: 첫 번째 variant
    return candidates[0].get("variantId")


def call_backend_recommend(condition: RecommendationCondition, auth_token: str) -> Optional[Dict[str, Any]]:
    try:
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/products/recommend"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        goal_value = condition.goal if condition.goal != "ALL" else None
        product_category_value = condition.product_category if condition.product_category != "ALL" else None
        keyword_value = None
        if getattr(condition, "keyword", None) and str(condition.keyword).strip():
            keyword_value = re.sub(r"\s+", " ", str(condition.keyword).strip())

        request_body = {
            "goal": goal_value,
            "productCategory": product_category_value,
            "budgetMax": condition.budget_max,
            "avoid": condition.avoid,
            "mustHave": condition.must_have,
            "priority": condition.priority
        }
        if keyword_value:
            request_body["keyword"] = keyword_value

        client = _get_http_client()
        response = client.post(url, json=request_body, headers=headers)
        response.raise_for_status()
        return response.json()
    
    except httpx.HTTPStatusError as e:
        print(f"Backend 상품 추천 호출 실패 (HTTP {e.response.status_code}): {e}")
        return None
    except Exception as e:
        print(f"Backend 상품 추천 호출 실패: {e}")
        return None


def handle_commerce_recommend(
    text: str,
    session_id: str,
    auth_token: Optional[str] = None
) -> Dict[str, Any]:
    session = state_machine.get_session(session_id)
    if not session:
        session = state_machine.create_session(session_id)

    if session.awaiting_since:
        elapsed = (datetime.now() - session.awaiting_since).total_seconds()
        if elapsed > 180:
            state_machine.delete_session(session_id)
            session = state_machine.create_session(session_id)

    current_state = session.state
    if current_state == CommerceState.RECOMMEND:
        return handle_recommend_state(text, session_id, auth_token)
    elif current_state == CommerceState.CONFIRM_PRODUCT:
        return handle_confirm_product_state(text, session_id, auth_token)
    elif current_state == CommerceState.ADD_TO_CART:
        return handle_add_to_cart_state(session_id, auth_token)
    elif current_state == CommerceState.CONFIRM_ADDRESS:
        return handle_confirm_address_state(text, session_id, auth_token)
    elif current_state == CommerceState.PAYMENT_READY:
        return handle_payment_ready_state(session_id, auth_token)
    else:
        return {
            "state": current_state.value,
            "message": "알 수 없는 상태입니다.",
            "error": "UNKNOWN_STATE"
        }


def handle_recommend_state(
    text: str,
    session_id: str,
    auth_token: Optional[str],
    pre_extracted_slots: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    t_recommend_start = time.time()
    try:
        session = state_machine.get_session(session_id)
        text_stripped = (text or "").strip()
        combined_condition: Optional[RecommendationCondition] = None

        if pre_extracted_slots is not None:
            extracted_slots = pre_extracted_slots
        else:
            if USE_COMBINED_CONDITION_LLM:
                try:
                    combined_slots_raw, combined_condition = generate_slots_and_condition_combined(
                        text_stripped or text,
                        extracted_slots_hint={},
                        auth_token=auth_token,
                        profile_context=None,
                    )
                    extracted_slots = _apply_slot_policy(combined_slots_raw)
                    print("[commerce] combined_condition_used=true")
                except Exception as e:
                    combined_condition = None
                    print(f"[commerce] combined_condition_failed: {e}")

            if combined_condition is None:
                raw_slots = extract_commerce_intent_and_slots(text_stripped or text)
                extracted_slots = _apply_slot_policy(raw_slots)

        # 직전 추천 결과 상태와, 이번 발화가 "완전히 새로운 추천 요청"인지 여부를 확인한다.
        prev_last_result_type = getattr(session, "last_result_type", None) if session else None
        is_new_request = _is_new_recommendation_request(text_stripped or text, extracted_slots)
        # 직전 결과가 품절/조건 불일치/상품 없음인데 이번 발화가 새 추천 요청이면,
        # 이전 슬롯(goal/category/keyword 등)을 초기화하고 이번 발화 기준으로 다시 잡는다.
        reset_prev_slots = bool(
            is_new_request
            and prev_last_result_type in {
                "PRODUCT_OUT_OF_STOCK",
                "CONDITION_NO_MATCH",
                "NO_PRODUCTS",
                "REJECTED_BY_USER",
            }
        )

        if reset_prev_slots:
            prev_goal = None
            prev_category = None
            prev_budget = None
            prev_keyword = None
            prev_variant_option = None
            prev_slot_avoid = []
            prev_address_mode = None
            prev_pending_action = None
            prev_recipient_name = None
        else:
            prev_goal = getattr(session, "goal_type", None) if session else None
            prev_category = getattr(session, "product_category", None) if session else None
            prev_budget = getattr(session, "budget_max", None) if session else None
            prev_keyword = getattr(session, "keyword", None) if session else None
            prev_variant_option = getattr(session, "variant_option", None) if session else None
            prev_slot_avoid = getattr(session, "slot_avoid", []) if session else []
            prev_address_mode = getattr(session, "address_mode", None) if session else None
            prev_pending_action = getattr(session, "pending_action", None) if session else None
            prev_recipient_name = getattr(session, "recipient_name", None) if session else None

        merged_goal = _normalize_goal(extracted_slots.get("goal") or prev_goal)
        merged_category = _normalize_product_category(extracted_slots.get("product_category") or prev_category)
        merged_budget = (
            extracted_slots.get("budget")
            if extracted_slots.get("budget") is not None
            else prev_budget
        )
        merged_keyword = extracted_slots.get("keyword") or prev_keyword
        new_avoid = extracted_slots.get("avoid", []) or []
        merged_slot_avoid = []
        for src in (prev_slot_avoid or []), (new_avoid or []):
            for item in src:
                if item not in merged_slot_avoid:
                    merged_slot_avoid.append(item)
        merged_variant_option = extracted_slots.get("variant_option") or prev_variant_option
        merged_address_mode = _normalize_address_mode(extracted_slots.get("address_mode") or prev_address_mode)
        merged_pending_action = _normalize_pending_action(extracted_slots.get("pending_action") or prev_pending_action)
        merged_recipient_name = extracted_slots.get("recipient_name") or prev_recipient_name
        info_lack = (
            merged_goal == "ALL"
            and merged_category == "ALL"
            and merged_budget is None
            and (not merged_keyword or not str(merged_keyword).strip())
        )
        update_kw: Dict[str, Any] = {
            "goal_type": merged_goal,
            "product_category": merged_category,
            "budget_max": merged_budget,
            "keyword": merged_keyword,
            "slot_avoid": merged_slot_avoid,
            "variant_option": merged_variant_option,
            "address_mode": merged_address_mode,
            "pending_action": merged_pending_action,
            "recipient_name": merged_recipient_name,
        }
        if info_lack:
            update_kw["last_result_type"] = "INFO_LACK"
            update_kw["awaiting_since"] = datetime.now()
        state_machine.update_session(session_id, **update_kw)
        if info_lack:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "어떤 목적과 어떤 종류의 상품을 원하시는지 말씀해 주세요. (예: 다이어트용 보충제, 운동복, 홈트 용품 등)",
                "error": "INFO_LACK",
            }

        needs_personalization = extracted_slots.get("needs_personalization", False)
        if auth_token and (needs_personalization or merged_goal == "ALL"):
            try:
                ensure_session_profile(session_id, auth_token)
            except Exception as e:
                print(f"[commerce] ensure_session_profile failed: {e}")

        session = state_machine.get_session(session_id)
        merged_slots = {
            "intent": extracted_slots.get("intent", "PRODUCT_RECOMMEND"),
            "goal": merged_goal,
            "product_category": merged_category,
            "budget": merged_budget,
            "avoid": merged_slot_avoid,
            "keyword": merged_keyword,
            "variant_option": merged_variant_option,
            "target_body_part": extracted_slots.get("target_body_part"),
            "product_usage": extracted_slots.get("product_usage"),
            "experience_level": extracted_slots.get("experience_level"),
        }
        reuse_condition = False
        existing_condition_dict = getattr(session, "recommendation_condition", None) if session else None
        if existing_condition_dict:
            try:
                prev_cond = RecommendationCondition.from_dict(existing_condition_dict)
                if (
                    prev_cond.goal == merged_goal
                    and prev_cond.product_category == merged_category
                    and prev_cond.budget_max == merged_budget
                    and (prev_cond.keyword or None) == (merged_keyword or None)
                    and (prev_cond.avoid or []) == (merged_slot_avoid or [])
                ):
                    reuse_condition = True
            except Exception:
                reuse_condition = False

        profile_context = None
        if session and any(
            [
                session.goal_type,
                session.member_gender,
                session.member_height_cm is not None,
                session.member_weight_kg is not None,
                session.budget_max is not None,
                bool(getattr(session, "profile_avoid", None)),
            ]
        ):
            profile_context = {
                "goal_type": session.goal_type,
                "member_gender": session.member_gender,
                "member_height_cm": session.member_height_cm,
                "member_weight_kg": session.member_weight_kg,
                "budget_max": session.budget_max,
                "profile_avoid": getattr(session, "profile_avoid", []) or [],
            }

        if reuse_condition:
            condition = RecommendationCondition.from_dict(existing_condition_dict)
        else:
            if combined_condition is not None:
                condition = combined_condition
                # merged 슬롯 값과 condition의 기본 필드 정합성 보정
                condition.goal = merged_goal
                condition.product_category = merged_category
                condition.budget_max = merged_budget
                condition.keyword = merged_keyword
                condition.avoid = merged_slot_avoid
            else:
                condition = generate_recommendation_condition(
                    text,
                    merged_slots,
                    auth_token=auth_token,
                    profile_context=profile_context,
                )
        # 대화에서 목적을 지정하지 않았을 때(goal=ALL) 프로필을 썼다면 user_profile_used=True, goal 등 반영
        if condition and profile_context and merged_goal == "ALL":
            condition.user_profile_used = True
            if profile_context.get("goal_type"):
                condition.goal = profile_context["goal_type"]
        if not condition:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "추천 조건을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.",
                "error": "CONDITION_GENERATION_FAILED"
            }

        if not auth_token:
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "인증이 필요합니다.",
                "error": "AUTH_REQUIRED"
            }
        
        print(f"[commerce] backend_recommend_request total_since_recommend={time.time() - t_recommend_start:.2f}s")
        backend_response = call_backend_recommend(condition, auth_token)

        def _extract_products(resp: Optional[Dict[str, Any]]) -> tuple[List[Dict[str, Any]], Optional[bool]]:
            if not resp:
                return [], None
            return (resp.get("products") or []), resp.get("conditionMatched")

        products, condition_matched = _extract_products(backend_response)

        # 1차 시도에서 상품이 없거나(conditionMatched=false + products=[]) 전혀 검색되지 않은 경우:
        # LLM을 사용해 soft 조건(budget_max, must_have, priority 등)을 완화한 fallback 조건으로 재검색을 시도한다.
        if (not backend_response) or (condition_matched is False and not products) or (not products):
            if not backend_response:
                state_machine.update_session(session_id, last_result_type="NO_PRODUCTS")
            elif condition_matched is False:
                state_machine.update_session(session_id, last_result_type="CONDITION_NO_MATCH")
            else:
                state_machine.update_session(session_id, last_result_type="NO_PRODUCTS")

            fallbacks = generate_fallback_conditions(text, condition)
            fallback_used = False
            for fb_cond in fallbacks:
                fb_response = call_backend_recommend(fb_cond, auth_token)
                fb_products, fb_matched = _extract_products(fb_response)
                if fb_products:
                    # fallback 조건으로 상품을 찾은 경우, 해당 조건/상품으로 계속 진행
                    condition = fb_cond
                    backend_response = fb_response
                    products = fb_products
                    condition_matched = fb_matched
                    fallback_used = True
                    break

            if not fallback_used:
                # fallback으로도 유의미한 상품을 찾지 못한 경우: 기존처럼 일반 챗으로 핸드오프
                handoff_message = _generate_handoff_to_general_message(text, getattr(condition, "keyword", None))
                result = {
                    "state": CommerceState.RECOMMEND.value,
                    "message": handoff_message,
                    "error": "CONDITION_NO_MATCH" if condition_matched is False else "NO_PRODUCTS_FOUND",
                    "handoff_to_general_chat": True,
                }
                alt = backend_response.get("alternativeCandidates") if backend_response else None
                if alt:
                    result["alternativeCandidates"] = _trim_recommended_products(alt)
                return result

        available_products = [
            p for p in products 
            if p.get("availableVariants") and len(p.get("availableVariants", [])) > 0
        ]
        
        if not available_products:
            state_machine.update_session(session_id, last_result_type="PRODUCT_OUT_OF_STOCK")
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": get_user_message_for_error("PRODUCT_OUT_OF_STOCK"),
                "error": "PRODUCT_OUT_OF_STOCK"
            }
        
        products = available_products
        selected_product = products[0]
        variants = selected_product.get("availableVariants") or []
        selected_variant_id = _pick_variant_id(variants, merged_variant_option) if variants else None
        option_name = None
        if selected_variant_id and variants:
            for v in variants:
                if v.get("variantId") == selected_variant_id:
                    option_name = v.get("name")
                    break

        # 확정된 condition을 세션에 저장 (latest_condition, recommendation_id, last_query_text 포함)
        rec_id = f"rec_{session_id}_{uuid.uuid4().hex[:8]}"
        normalized_query = condition.to_normalized_query_text(text)
        state_machine.update_session(
            session_id,
            recommendation_condition=condition.to_dict(),
            latest_condition=condition.to_dict(),
            recommendation_id=rec_id,
            last_query_text=normalized_query,
            recommended_products=_trim_recommended_products(products),
            selected_product_id=selected_product.get("productId"),
            selected_variant_id=selected_variant_id,
            variant_option=option_name if option_name else merged_variant_option,
        )
        state_machine.transition_state(session_id, CommerceState.CONFIRM_PRODUCT)
        state_machine.update_session(session_id, awaiting_since=datetime.now())
        product_name = selected_product.get("name", "상품")
        option_part = f" ({option_name})" if option_name else ""
        product_display = f"{product_name}{option_part}"

        if merged_address_mode == "DEFAULT":
            address_phrase = "기본 배송지로 "
        elif merged_address_mode == "NEW":
            address_phrase = "새로운 배송지로 "
        else:
            address_phrase = ""

        reason = _build_reason_text(condition)
        josa = _josa(product_display, "을", "를")
        confirm_verb = "보내드릴까요" if address_phrase else "결제할까요"
        message = f"{reason}\n\n{product_display}{josa} {address_phrase}{confirm_verb}? (예 / 아니오)"
        
        # 주문 초안 생성 (참고용 - 실제 주문 시 백엔드에서 재검증)
        order_draft = build_order_draft(
            recommendation_id=rec_id,
            selected_product_id=selected_product.get("productId"),
            selected_variant_id=selected_variant_id,
            quantity=1,
            product_name=product_name,
            variant_name=option_name,
        )
        
        # 추천 생성 이벤트 로깅
        latency_ms = (time.time() - t_recommend_start) * 1000
        log_recommendation_generated(
            session_id=session_id,
            recommendation_id=rec_id,
            condition=condition.to_dict(),
            products=products,
            latency_ms=latency_ms,
        )
        
        return {
            "state": CommerceState.CONFIRM_PRODUCT.value,
            "message": message,
            "products": [selected_product],
            "recommendation_condition": condition.to_dict(),
            "recommendation_id": rec_id,
            "order_draft": order_draft,
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "recommend")
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"RECOMMEND 상태 처리 실패: {e}")
        error_info = handle_exception(e, "recommend")
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_confirm_product_state(
    text: str,
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    session = state_machine.get_session(session_id)
    if not session:
        state_machine.create_session(session_id)
        return handle_recommend_state(text, session_id, auth_token)

    text_stripped = (text or "").strip()
    text_lower = text_stripped.lower()

    # "다른 옵션 있어?", "다른 색/사이즈로" 등 옵션 변경 요청 → 긍정(예)보다 먼저 처리("있어"에 "어" 포함되어 예로 오인되는 것 방지)
    option_request_phrases = (
        "다른 옵션", "다른 옵션 있어", "옵션 있어", "다른 색", "다른 사이즈", "다른 걸로",
        "다른 색으로", "다른 사이즈로", "옵션 보여", "다른 거 있어", "다른 거 보여",
    )
    if any(phrase in text_lower for phrase in option_request_phrases):
        products_list = session.recommended_products or []
        if products_list:
            first_product = products_list[0]
            variants = first_product.get("availableVariants") or []
            if len(variants) > 1:
                product_id = first_product.get("productId")
                product_name = first_product.get("name", "")
                option_candidates = [
                    {
                        "productId": product_id,
                        "productName": product_name,
                        "variantId": v.get("variantId"),
                        "variantName": v.get("name", ""),
                    }
                    for v in variants
                ]
                state_machine.update_session(session_id, awaiting_since=datetime.now())
                return {
                    "state": CommerceState.CONFIRM_PRODUCT.value,
                    "message": "다른 옵션을 골라주세요. 원하는 옵션을 말씀해 주시면 해당 옵션으로 결제 진행할게요.",
                    "products": products_list[:1],
                    "optionCandidates": option_candidates,
                }
            # 옵션이 1개뿐이면 안내만
            if len(variants) == 1:
                state_machine.update_session(session_id, awaiting_since=datetime.now())
                return {
                    "state": CommerceState.CONFIRM_PRODUCT.value,
                    "message": "현재 이 상품은 선택 가능한 옵션이 하나뿐이에요. 이 옵션으로 결제할까요? (예 / 아니오)",
                    "products": products_list[:1],
                }

    positive_responses = ["응", "예", "어", "좋아", "구매", "살래", "네", "맞아", "그래"]
    negative_responses = ["아니", "안돼", "싫어", "안 할래", "취소"]
    is_positive = any(pos in text_lower for pos in positive_responses)
    is_negative = any(neg in text_lower for neg in negative_responses)
    if is_positive:
        state_machine.transition_state(session_id, CommerceState.ADD_TO_CART)
        return handle_add_to_cart_state(session_id, auth_token)
    if is_negative:
        # 사용자 거절 이벤트 로깅
        rec_id = getattr(session, "recommendation_id", None)
        log_user_rejected(
            session_id=session_id,
            recommendation_id=rec_id,
            reason="user_said_no",
        )
        
        state_machine.transition_state(session_id, CommerceState.RECOMMEND)
        state_machine.update_session(
            session_id,
            last_result_type="REJECTED_BY_USER",
            awaiting_since=None,
        )
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "알겠습니다. 다른 상품이 필요하시면 언제든지 말씀해주세요.",
            "error": None,
        }

    # 문장형 발화: 새 추천 요청이면 RECOMMEND로 전환
    if _is_sentence_like_for_confirm(text_stripped):
        try:
            extracted = extract_commerce_intent_and_slots(text_stripped)
            extracted_slots = _apply_slot_policy(extracted)
            if _is_new_recommendation_request(text_stripped, extracted_slots):
                state_machine.transition_state(session_id, CommerceState.RECOMMEND)
                state_machine.update_session(session_id, awaiting_since=None)
                return handle_recommend_state(text_stripped, session_id, auth_token, pre_extracted_slots=extracted_slots)
        except Exception as e:
            print(f"[commerce] CONFIRM_PRODUCT extract_commerce_intent_and_slots failed: {e}")

    state_machine.update_session(session_id, awaiting_since=datetime.now())
    return {
        "state": CommerceState.CONFIRM_PRODUCT.value,
        "message": "구매하시겠어요? (예/아니오)",
        "products": session.recommended_products[:1] if session.recommended_products else [],
    }


def handle_add_to_cart_state(
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    session = state_machine.get_session(session_id)
    if not session or not session.selected_product_id:
        state_machine.create_session(session_id)
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "어떤 상품을 도와드릴까요?",
            "error": None,
        }

    if not auth_token:
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }

    try:
        idempotency_key = f"{session_id}:{session.selected_product_id}:{session.selected_variant_id}:{int(time.time())}"
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/cart/ai/add-item"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json",
            "Idempotency-Key": idempotency_key
        }
        
        request_body = {
            "productId": session.selected_product_id,
            "variantId": session.selected_variant_id,
            "qty": 1
        }
        
        client = _get_http_client()
        response = client.post(url, json=request_body, headers=headers)
        response.raise_for_status()
        state_machine.transition_state(session_id, CommerceState.CONFIRM_ADDRESS)
        
        return handle_confirm_address_state(None, session_id, auth_token)
    
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 409:
            state_machine.transition_state(session_id, CommerceState.CONFIRM_ADDRESS)
            return handle_confirm_address_state(None, session_id, auth_token)
        else:
            error_info = handle_exception(e, "cart_add")
            return {
                "state": CommerceState.ADD_TO_CART.value,
                "message": error_info["message"],
                "error": error_info["error"]
            }
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "cart_add")
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"장바구니 담기 실패: {e}")
        error_info = handle_exception(e, "cart_add")
        return {
            "state": CommerceState.ADD_TO_CART.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_confirm_address_state(
    text: Optional[str],
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    """CONFIRM_ADDRESS 상태 처리"""
    if not auth_token:
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }

    try:
        session = state_machine.get_session(session_id)
        if not session:
            state_machine.create_session(session_id)
            return {
                "state": CommerceState.RECOMMEND.value,
                "message": "어떤 상품을 도와드릴까요?",
                "error": None,
            }
        address_mode = getattr(session, "address_mode", None) if session else None
        pending_action = getattr(session, "pending_action", None) if session else None
        recipient_name = getattr(session, "recipient_name", None) if session else None
        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        url = f"{BACKEND_BASE_URL}/api/member-addr-info/me"
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        
        client = _get_http_client()
        response = client.get(url, headers=headers)
        response.raise_for_status()
        addresses = response.json()
        if not addresses or len(addresses) == 0:
            state_machine.update_session(session_id, last_result_type="NO_ADDRESS")
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": "저장된 배송지가 없어 AI로 주문을 진행할 수 없어요. 마이페이지에서 배송지를 먼저 등록해 주세요.",
                "error": "NO_ADDRESS",
                "requires_address_input": False
            }

        address_candidates = _format_address_candidates(addresses)
        default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        recipient_address = None
        if recipient_name:
            rn_norm = str(recipient_name).strip().lower()
            for addr in addresses:
                name = str(addr.get("shipToName") or "").strip().lower()
                if rn_norm and rn_norm in name:
                    recipient_address = addr
                    break

        # address_mode/recipient_name을 고려하여 이번에 보여줄 후보 주소 결정
        candidate_address = default_address
        if recipient_address is not None:
                candidate_address = recipient_address

        ship_to_name = candidate_address.get('shipToName', '')
        address1 = candidate_address.get('shipAddress1', '')
        address2 = candidate_address.get('shipAddress2', '')
        address_text = f"{address1} {address2}".strip()
        if ship_to_name:
            address_display = f"{ship_to_name} {address_text}"
        else:
            address_display = address_text

        if text:
            text_stripped = text.strip()
            text_lower = text_stripped.lower()
            cancel_phrases = ["취소", "주문 취소", "그만할게", "그만", "안 할게", "안할게", "취소할게", "취소할게요"]
            if any(c in text_lower for c in cancel_phrases):
                state_machine.delete_session(session_id)
                return {
                    "state": CommerceState.RECOMMEND.value,
                    "message": "주문을 취소했어요. 다른 상품이 필요하시면 언제든지 말씀해 주세요.",
                    "error": "FLOW_CANCELLED",
                }

            # 1차: 문장 길이/형태와 상관없이 recipient_name 기반 배송지 매칭을 먼저 시도한다.
            # (예: "이젠아카데미" 한 단어만 말해도, 해당 이름의 저장된 배송지로 인식)
            try:
                extracted_for_recipient = extract_commerce_intent_and_slots(text_stripped)
            except Exception:
                extracted_for_recipient = {}
            new_recipient_generic = extracted_for_recipient.get("recipient_name")
            if isinstance(new_recipient_generic, str) and new_recipient_generic.strip():
                rn_norm_new = new_recipient_generic.strip().lower()
                matched_addr = None
                for addr in addresses:
                    name = str(addr.get("shipToName") or "").strip().lower()
                    if rn_norm_new and rn_norm_new in name:
                        matched_addr = addr
                        break
                if matched_addr:
                    ship_to_name2 = matched_addr.get("shipToName", "")
                    addr1b = matched_addr.get("shipAddress1", "")
                    addr2b = matched_addr.get("shipAddress2", "")
                    addr_text2 = f"{addr1b} {addr2b}".strip()
                    addr_disp2 = f"{ship_to_name2} {addr_text2}" if ship_to_name2 else addr_text2
                    state_machine.update_session(
                        session_id,
                        recipient_name=new_recipient_generic.strip(),
                        awaiting_since=datetime.now(),
                    )
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"배송지: {addr_disp2}로 배송하시겠어요?",
                        "address": matched_addr,
                        "addressCandidates": address_candidates,
                    }

            # 수취인/배송지 지시("OO한테 보내줘" 등)는 intent 재분류보다 먼저 처리해 OFF_TOPIC으로 빠지지 않도록 함
            delivery_instruction_phrases = ("한테", "에게", "보내줘", "보내주세요", "배송해줘", "로 보내")
            if any(phrase in text_lower for phrase in delivery_instruction_phrases):
                try:
                    extracted = extract_commerce_intent_and_slots(text_stripped)
                    new_recipient = extracted.get("recipient_name")
                    if isinstance(new_recipient, str) and new_recipient.strip():
                        rn_norm_new = new_recipient.strip().lower()
                        matched_addr = None
                        for addr in addresses:
                            name = str(addr.get("shipToName") or "").strip().lower()
                            if rn_norm_new and rn_norm_new in name:
                                matched_addr = addr
                                break
                        if matched_addr:
                            ship_to_name2 = matched_addr.get("shipToName", "")
                            addr1b = matched_addr.get("shipAddress1", "")
                            addr2b = matched_addr.get("shipAddress2", "")
                            addr_text2 = f"{addr1b} {addr2b}".strip()
                            addr_disp2 = f"{ship_to_name2} {addr_text2}" if ship_to_name2 else addr_text2
                            state_machine.update_session(
                                session_id,
                                recipient_name=new_recipient.strip(),
                                awaiting_since=datetime.now(),
                            )
                            return {
                                "state": CommerceState.CONFIRM_ADDRESS.value,
                                "message": f"배송지: {addr_disp2}로 배송하시겠어요?",
                                "address": matched_addr,
                                "addressCandidates": address_candidates,
                            }
                except Exception as e:
                    print(f"[commerce] CONFIRM_ADDRESS recipient extract/match failed: {e}")

            # 문장형 발화: 새 추천 요청이면 RECOMMEND로 전환
            if _is_sentence_like_for_confirm(text_stripped):
                try:
                    extracted = extract_commerce_intent_and_slots(text_stripped)
                    extracted_slots = _apply_slot_policy(extracted)
                    if _is_new_recommendation_request(text_stripped, extracted_slots):
                        state_machine.transition_state(session_id, CommerceState.RECOMMEND)
                        state_machine.update_session(session_id, awaiting_since=None)
                        return handle_recommend_state(text_stripped, session_id, auth_token, pre_extracted_slots=extracted_slots)
                except Exception as e:
                    print(f"[commerce] CONFIRM_ADDRESS extract_commerce_intent_and_slots failed: {e}")

            positive_responses = ["응", "예", "어", "좋아", "맞아", "그래", "네"]
            negative_responses = ["아니", "아니요", "싫어", "다른 데", "다른데", "다른 주소", "거긴 말고"]
            if any(pos in text_lower for pos in positive_responses):
                state_machine.update_session(session_id, address_id=candidate_address.get("id"))
                state_machine.transition_state(session_id, CommerceState.PAYMENT_READY)
                return handle_payment_ready_state(session_id, auth_token)
            if any(neg in text_lower for neg in negative_responses):
                # 동일 세션에서 이미 한 번 주소를 거부한 상태에서 다시 부정 응답이 오면 플로우를 종료한다.
                prev_last_result = getattr(session, "last_result_type", None)
                if prev_last_result == "ADDRESS_REJECTED":
                    state_machine.delete_session(session_id)
                    return {
                        "state": CommerceState.RECOMMEND.value,
                        "message": "주문을 취소했어요. 다른 상품이 필요하시면 언제든지 말씀해 주세요.",
                        "error": "FLOW_CANCELLED",
                    }

                state_machine.update_session(session_id, last_result_type="ADDRESS_REJECTED")
                if recipient_address is None:
                    state_machine.update_session(session_id, awaiting_since=datetime.now())
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"기본 배송지 외에 다른 저장된 주소로 보내시려면, 마이페이지에서 주소를 추가해 주세요.\n지금 기본 배송지({address_display})로 보내실까요?",
                        "address": default_address,
                        "addressCandidates": address_candidates,
                    }
                else:
                    state_machine.update_session(session_id, awaiting_since=datetime.now())
                    return {
                        "state": CommerceState.CONFIRM_ADDRESS.value,
                        "message": f"이젠 다른 저장된 배송지로만 보낼 수 있어요. 마이페이지에서 주소를 추가하신 후 다시 시도해 주세요.\n현재 기본 배송지({address_display})로 보내실까요?",
                        "address": default_address,
                        "addressCandidates": address_candidates,
                    }

        # 텍스트가 없거나 위의 분기들에서 처리되지 않은 경우 기본 안내를 보낸다.
        state_machine.update_session(session_id, awaiting_since=datetime.now())
        if address_mode == "NEW":
            return {
                "state": CommerceState.CONFIRM_ADDRESS.value,
                "message": f"지금은 미리 저장된 배송지로만 보낼 수 있어요. 다른 곳으로 보내시려면 마이페이지에서 배송지를 추가해 주세요.\n기본 배송지({address_display})로 보내실까요?",
                "address": default_address,
                "addressCandidates": address_candidates,
            }

        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": f"배송지: {address_display}로 배송하시겠어요?",
            "address": candidate_address,
            "addressCandidates": address_candidates,
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "address_check")
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"배송지 확인 실패: {e}")
        error_info = handle_exception(e, "address_check")
        return {
            "state": CommerceState.CONFIRM_ADDRESS.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }


def handle_payment_ready_state(
    session_id: str,
    auth_token: Optional[str]
) -> Dict[str, Any]:
    session = state_machine.get_session(session_id)
    if not session:
        state_machine.create_session(session_id)
        return {
            "state": CommerceState.RECOMMEND.value,
            "message": "어떤 상품을 도와드릴까요?",
            "error": None,
        }

    if not auth_token:
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": "인증이 필요합니다.",
            "error": "AUTH_REQUIRED"
        }

    try:
        if session.order_no:
            if not auth_token.startswith("Bearer "):
                auth_token = f"Bearer {auth_token}"
            
            headers = {
                "Authorization": auth_token,
                "Content-Type": "application/json"
            }
            
            payment_url = f"{BACKEND_BASE_URL}/api/orders/{session.order_no}/pay/ready"
            client = _get_http_client()
            payment_response = client.post(payment_url, headers=headers)
            payment_response.raise_for_status()
            payment_data = payment_response.json()
            state_machine.delete_session(session_id)

            return {
                "state": CommerceState.PAYMENT_READY.value,
                "message": "결제 페이지로 이동합니다.",
                "payment_ready": payment_data,
                "order_no": session.order_no,
                "error": "FLOW_COMPLETED",
            }

        if not auth_token.startswith("Bearer "):
            auth_token = f"Bearer {auth_token}"
        
        headers = {
            "Authorization": auth_token,
            "Content-Type": "application/json"
        }
        address_url = f"{BACKEND_BASE_URL}/api/member-addr-info/me"
        client = _get_http_client()
        address_response = client.get(address_url, headers=headers)
        address_response.raise_for_status()
        addresses = address_response.json()
        if not addresses:
            return {
                "state": CommerceState.PAYMENT_READY.value,
                "message": "저장된 배송지가 없어 AI로 주문을 진행할 수 없어요. 마이페이지에서 배송지를 먼저 등록해 주세요.",
                "error": "NO_ADDRESS",
            }

        default_address = next((addr for addr in addresses if addr.get("isDefault")), addresses[0])
        selected_address = default_address
        selected_address_id = getattr(session, "address_id", None)
        if selected_address_id is not None:
            for addr in addresses:
                if addr.get("id") == selected_address_id:
                    selected_address = addr
                    break

        profile_url = f"{BACKEND_BASE_URL}/api/members/me/profile"
        client = _get_http_client()
        profile_response = client.get(profile_url, headers=headers)
        profile_response.raise_for_status()
        profile_data = profile_response.json()
        
        order_url = f"{BACKEND_BASE_URL}/api/orders/from-cart"
        order_request = {
            "shipTo": {
                "recipientName": selected_address.get("shipToName"),
                "recipientPhone": selected_address.get("shipToPhone"),
                "zipcode": selected_address.get("shipZipcode"),
                "address1": selected_address.get("shipAddress1"),
                "address2": selected_address.get("shipAddress2", ""),
            },
            "buyer": {
                "buyerName": profile_data.get("name", ""),
                "buyerEmail": profile_data.get("email", ""),
                "buyerPhone": selected_address.get("shipToPhone", ""),
            },
        }
        client = _get_http_client()
        order_response = client.post(order_url, json=order_request, headers=headers)
        order_response.raise_for_status()
        order_data = order_response.json()
        order_no = order_data.get("orderNo")
        payment_url = f"{BACKEND_BASE_URL}/api/orders/{order_no}/pay/ready"
        payment_response = client.post(payment_url, headers=headers)
        payment_response.raise_for_status()
        payment_data = payment_response.json()
        state_machine.delete_session(session_id)

        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": "결제 페이지로 이동합니다.",
            "payment_ready": payment_data,
            "order_no": order_no,
            "error": "FLOW_COMPLETED",
        }
    
    except httpx.TimeoutException as e:
        error_info = handle_exception(e, "payment_ready")
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }
    except Exception as e:
        print(f"결제 ready 실패: {e}")
        error_info = handle_exception(e, "payment_ready")
        return {
            "state": CommerceState.PAYMENT_READY.value,
            "message": error_info["message"],
            "error": error_info["error"]
        }



