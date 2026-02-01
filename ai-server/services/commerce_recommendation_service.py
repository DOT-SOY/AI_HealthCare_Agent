"""
Commerce 상품 추천 조건 생성 서비스
"""
from typing import Dict, Any, Optional, List
from services.ai_service import call_ai_json
from services.commerce_rag_service import search_commerce_rag
from services.backend_client import get_user_profile
from prompts.commerce_recommendation import build_system_prompt, build_user_prompt
from schemas.recommendation_schema import RecommendationCondition


def generate_recommendation_condition(
    user_text: str,
    extracted_slots: Dict[str, Any],
    auth_token: Optional[str] = None
) -> Optional[RecommendationCondition]:
    """
    추천 조건 생성
    
    Args:
        user_text: 사용자 발화
        extracted_slots: 추출된 slot 정보
        auth_token: 인증 토큰 (사용자 프로필 조회용)
    
    Returns:
        RecommendationCondition 또는 None (실패 시)
    """
    try:
        # 1. 사용자 프로필 조회 (auth_token이 있는 경우)
        user_profile = None
        if auth_token:
            user_profile = get_user_profile(auth_token)
        
        # 2. RAG 컨텍스트 검색
        goal = extracted_slots.get("goal", "ALL")
        product_category = extracted_slots.get("product_category", "ALL")
        doc_types = ["goal_playbook", "category_guide", "safety_policy"]
        
        rag_results = search_commerce_rag(
            goal=goal if goal != "ALL" else None,
            product_category=product_category if product_category != "ALL" else None,
            doc_types=doc_types,
            query_text=user_text,
            limit=10
        )
        
        # RAG 컨텍스트 텍스트 생성
        rag_context_parts = []
        for result in rag_results:
            if result.get("title"):
                rag_context_parts.append(f"## {result['title']}")
            if result.get("content"):
                rag_context_parts.append(result["content"])
            if result.get("section"):
                rag_context_parts.append(f"\n섹션: {result['section']}")
            rag_context_parts.append("")
        
        rag_context = "\n".join(rag_context_parts) if rag_context_parts else "추천 규칙 정보가 없습니다."
        
        # 3. 프롬프트 생성
        system_prompt = build_system_prompt(rag_context, user_profile or {})
        user_prompt = build_user_prompt(user_text, extracted_slots)
        
        # 4. LLM 호출 (JSON만 반환 강제)
        result = call_ai_json(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            temperature=0.3  # 일관성과 창의성의 균형
        )
        
        # 5. RecommendationCondition 생성
        condition = RecommendationCondition.from_dict(result)
        
        # 6. 유효성 검증
        if not condition.validate():
            print("추천 조건 유효성 검증 실패")
            return None
        
        return condition
        
    except Exception as e:
        print(f"추천 조건 생성 실패: {e}")
        return None

