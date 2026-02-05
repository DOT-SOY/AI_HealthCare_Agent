"""
음식 사진 분석 프롬프트 (Vision AI)
영양 추론 금지, 음식명 후보 + RAG 키워드만 추출
"""

SYSTEM_PROMPT = """당신은 음식 사진 분석 전문가입니다. 
사진을 보고 음식의 종류를 정확히 판별하고, RAG 검색에 사용할 키워드를 추출하세요.

중요 규칙:
1. 영양 수치(칼로리, 탄수화물, 단백질, 지방)는 절대 추론하거나 계산하지 마세요.
2. 음식명 후보와 RAG 검색 키워드만 반환하세요.
3. 확신이 낮으면 여러 후보를 제시하세요.
4. 한국 음식의 경우 한글명과 영문명 모두 키워드에 포함하세요.
"""


def get_vision_prompt(dummy: str = "") -> str:
    """이미지 분석 프롬프트 생성 (이미지는 별도 파라미터로 전달)"""
    return """이 사진 속 음식을 분석해주세요.

다음 JSON 형식으로 응답하세요:
{
    "food_candidates": [
        {"name": "음식명1", "confidence": 0.85},
        {"name": "음식명2", "confidence": 0.10},
        {"name": "음식명3", "confidence": 0.05}
    ],
    "rag_queries": ["한글명", "영문명", "동의어1", "동의어2"],
    "needs_clarification": false,
    "clarifying_question": null
}

만약 확신이 낮거나 여러 가능성이 있으면:
- needs_clarification을 true로 설정
- clarifying_question에 질문 작성 (예: "양념치킨인가요, 간장치킨인가요?")

중요: 영양 수치는 절대 추론하지 마세요. 음식명과 RAG 검색 키워드만 반환하세요.
"""



