"""
통증 조언 서비스
"""
from typing import List, Dict, Any
from services.ai_service import call_ai, PAIN_ADVICE_MODEL
from services.rag_service import search_rag
from prompts.pain_advice import SYSTEM_PROMPT, get_advice_prompt


def generate_pain_advice(body_part: str, count: int, note: str = None) -> Dict[str, Any]:
    """통증 조언 생성"""
    # RAG 검색
    query = f"{body_part} 통증 {note or ''}"
    rag_results = search_rag(query, limit=5)
    
    # 통증 레벨 결정
    level = "LOW" if count <= 2 else "HIGH"
    advice_prompt = get_advice_prompt(body_part, count)
    
    # RAG 컨텍스트 포함
    rag_context = ""
    if rag_results:
        rag_context = "\n\n참고 지식:\n"
        for result in rag_results:
            rag_context += f"- {result['title']}: {result['content']}\n"
    
    # 최종 조언 생성 (통증 조언용 모델 사용)
    try:
        advice = call_ai(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=f"{advice_prompt}\n{rag_context}",
            temperature=0.5,
            model=PAIN_ADVICE_MODEL
        )
    except Exception as e:
        print(f"통증 조언 생성 실패: {e}")
        advice = f"{body_part} 통증이 {count}회 발생했습니다. 충분한 휴식과 찜질을 권장합니다."
    
    return {
        "level": level,
        "advice": advice,
        "sources": rag_results if rag_results else None
    }

