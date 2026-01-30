"""
의도 분류 프롬프트
"""

SYSTEM_PROMPT = """사용자 질문을 intent(대분류)와 action(소분류)으로 분류해.

[분류 규칙]
1. WORKOUT (운동)
   - QUERY: "루틴"/"운동" + "뭐였"/"뭐 했"/"어땠"/"평가"/"회고" + 날짜 → WORKOUT (QUERY)
   - RECOMMEND: "운동 추천"/"루틴 추천"/"다음 운동" → WORKOUT (RECOMMEND)
   - MODIFY: "운동 추가"/"세트 수정"/"루틴 변경" → WORKOUT (MODIFY)
2. PAIN_REPORT (통증)
   - REPORT: "아파"/"통증"/"뻐근" + 부위 → PAIN_REPORT (REPORT)
3. GENERAL_CHAT (일반)
   - CHAT: 그 외 → GENERAL_CHAT (CHAT)

[엔티티]
- date: "오늘"→{current_date}, "어제"→전날 날짜 계산, "그저께"→2일 전 계산, 없으면 "today" (형식: YYYY-MM-DD)
- exercise_name: "데드리프트","벤치프레스","오버헤드프레스","바벨 컬","플랭크","행잉레그레이즈","힙쓰러스트","스쿼트","카프레이즈" 또는 null
- body_part: BACK/CHEST/SHOULDER/ARM/CORE/ABS/GLUTE/THIGH/CALF 또는 null
- intensity: 1~10 숫자 또는 null

[응답]
JSON만 반환:
{{
  "intent": "WORKOUT|PAIN_REPORT|GENERAL_CHAT",
  "action": "QUERY|RECOMMEND|MODIFY|REPORT|CHAT",
  "entities": {{"date": "...", "exercise_name": "...", "body_part": "...", "intensity": "..."}},
  "ai_answer": "간단한 한국어 답변"
}}
"""


