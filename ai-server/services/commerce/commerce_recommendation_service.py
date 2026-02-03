"""
Commerce 상품 추천 조건 생성 서비스
- RAG는 상품이 아니라 **추천 기준/정책** 문서만 조회하고, 그 컨텍스트를 기반으로 조건만 생성한다.
"""
import time
from typing import Dict, Any, Optional, List

from services.ai_service import call_ai_json
from services.backend_client import get_user_profile
from .commerce_rag_service import search_commerce_rag, COMMERCE_POLICY_DOC_TYPES
from prompts.commerce.commerce_recommendation import build_system_prompt, build_user_prompt
from schemas.commerce.recommendation_schema import RecommendationCondition


def generate_recommendation_condition(
    user_text: str,
    extracted_slots: Dict[str, Any],
    auth_token: Optional[str] = None,
    profile_context: Optional[Dict[str, Any]] = None,
) -> Optional[RecommendationCondition]:
    """
    추천 조건 생성.
    우선순위: 1) profile_context 2) auth_token으로 프로필 조회 3) 프로필 없이 진행
    """
    t0 = time.time()
    try:
        user_profile: Optional[Dict[str, Any]] = None
        if profile_context is not None:
            user_profile = {
                "goal": profile_context.get("goal_type"),
                "heightCm": profile_context.get("member_height_cm"),
                "weightKg": profile_context.get("member_weight_kg"),
                "budgetMax": profile_context.get("budget_max") or profile_context.get("budgetMax"),
                "avoid": list(profile_context.get("profile_avoid") or []),
                "gender": profile_context.get("member_gender"),
            }
            print(f"[commerce] recommend_condition_start using_session_profile total={time.time() - t0:.2f}s")
        elif auth_token:
            t_profile_start = time.time()
            user_profile = get_user_profile(auth_token)
            print(
                f"[commerce] profile_fetch done elapsed={time.time() - t_profile_start:.2f}s "
                f"total={time.time() - t0:.2f}s"
            )
        else:
            print(f"[commerce] recommend_condition_start (no profile) total={time.time() - t0:.2f}s")

        t_rag_start = time.time()
        goal = extracted_slots.get("goal", "ALL")
        product_category = extracted_slots.get("product_category", "ALL")
        doc_types = COMMERCE_POLICY_DOC_TYPES

        rag_out = search_commerce_rag(
            goal=goal if goal != "ALL" else None,
            product_category=product_category if product_category != "ALL" else None,
            doc_types=doc_types,
            query_text=user_text,
            limit=5
        )
        rag_results = rag_out.get("results") or []
        rag_hit = rag_out.get("rag_hit", False)
        retrieved_chunks = rag_out.get("retrieved_chunks", 0)
        rag_error = rag_out.get("error")

        if not rag_hit:
            print(f"[commerce] rag_done rag_hit=false retrieved_chunks=0 error={rag_error!r} elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")
        else:
            print(f"[commerce] rag_done rag_hit=true retrieved_chunks={retrieved_chunks} elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")

        top_results = sorted(
            rag_results,
            key=lambda r: r.get("score", 0.0),
            reverse=True,
        )[:3]

        rag_context_parts = []
        for result in top_results:
            if result.get("title"):
                rag_context_parts.append(f"## {result['title']}")
            if result.get("content"):
                content = str(result["content"])
                if len(content) > 500:
                    content = content[:500] + " ..."
                rag_context_parts.append(content)
            if result.get("section"):
                rag_context_parts.append(f"\n섹션: {result['section']}")
            rag_context_parts.append("")

        if rag_context_parts:
            rag_context = "\n".join(rag_context_parts)
        else:
            rag_context = "추천 규칙 정보가 없습니다. (RAG 검색 미사용 또는 결과 없음)"
        print(f"[commerce] rag_context built elapsed={time.time() - t_rag_start:.2f}s total={time.time() - t0:.2f}s")

        system_prompt = build_system_prompt(rag_context, user_profile or {})
        user_prompt = build_user_prompt(user_text, extracted_slots)

        t_llm_start = time.time()
        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            temperature=0.3
        )
        print(f"[commerce] llm_done elapsed={time.time() - t_llm_start:.2f}s total={time.time() - t0:.2f}s")

        condition = RecommendationCondition.from_dict(result)

        if not condition.validate():
            print("추천 조건 유효성 검증 실패")
            return None

        return condition

    except Exception as e:
        print(f"추천 조건 생성 실패: {e}")
        return None
