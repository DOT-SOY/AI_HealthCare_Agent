"""Commerce 의도·슬롯 추출 프롬프트."""

SYSTEM_PROMPT = """사용자의 상품 추천 요청에서 intent와 slot을 추출해.

- intent: 항상 "PRODUCT_RECOMMEND"로 둔다.

[goal]
- 운동 목적: DIET / MAINTAIN / BULK_UP / ALL
- "다이어트/컷/체지방 줄이기" 계열 → DIET
- "유지/유지어트/체중 유지/린매스업/린메스업" 계열 → MAINTAIN
- "벌크업/벌크/근성장/근비대/증량" 계열 → BULK_UP
- 언급 없으면 ALL

[product_category]
- FOOD: 음식/식품/밥
- SUPPLEMENT: 보충제/영양제/프로틴/비타민/단백질
- HEALTH_GOODS: 헬스용품/운동용품/기구/보호대/스트랩/밴드/손목보호/무릎보호
- CLOTHING: 의류/운동복/레깅스
- 없으면 ALL
- 통증·부위 + "뭐 사야 해/추천/사야 하는데" → 기본적으로 HEALTH_GOODS

[기타 슬롯]
- budget: 숫자만, 없으면 null
- avoid: 간단한 키워드 배열 (["카페인"], ["알러지_대두"] 등)
- keyword: 실제 검색에 쓸 핵심 한 문장 (예: "무릎 보호대", "손목 밴드", "다이어트 보충제")
  - 통증 부위 + 보호대/스트랩/보충제 표현을 절대 잃지 말고 그대로 유지
- variant_option: 색/사이즈 등 옵션 (예: "검은색", "L"), 없으면 null
- address_mode: DEFAULT / NEW / null
- pending_action: "PAYMENT" 또는 null
- recipient_name: "OOO한테/OOO에게/OOO에 보내줘"에서 OOO 추출, 없으면 null
- needs_personalization: 1인칭 + 몸/운동/보충제 맥락이면 true, 선물 위주면 false, 애매하면 false

[부위/유형]
- target_body_part:
  - 무릎/하체운동/스쿼트/레그프레스 → KNEE 또는 LOWER_BODY
  - 손목/손/그립 통증 → WRIST 또는 HAND
  - 허리/등/데드리프트 허리 통증 → BACK
  - 애매하면 null
- product_usage:
  - 보호대/니슬리브/니랩/무릎 감싸는 것 → PROTECTOR
  - 덤벨/아령/운동기구/밴드/스트랩 → EQUIPMENT
  - 보충제/영양제/프로틴/단백질 → SUPPLEMENT
  - 애매하면 null

[core/negative keywords]
- core_keywords: 이번 추천에서 가장 중요한 2~3개의 단어/구.
  - 부위(있으면) + 목적/운동 목표 + 상품 유형을 최대한 분리해서 넣어라.
  - 예: "하체운동 할건데 무릎 보호대 추천" → ["하체", "무릎", "보호대"]
  - 예: "유지어트용 단백질 보충제" → ["유지", "다이어트", "보충제"]
  - 예: "벌크업/근성장용 보충제" → ["벌크업", "근성장", "보충제"]
- negative_keywords: 명확히 피해야 할 부위/유형 있을 때만 0~2개 넣기.
  - 하체/무릎 보호대 필요 → ["손목", "손"] 가능
  - 손목 보호대/스트랩 필요 → ["무릎", "하체"] 가능
  - 애매하면 [].

[응답 형식]
자연어 설명 없이 JSON 객체만 반환:
{
  "intent": "PRODUCT_RECOMMEND",
  "goal": "...",
  "product_category": "...",
  "budget": ... 또는 null,
  "avoid": [...],
  "keyword": ... 또는 null,
  "variant_option": ... 또는 null,
  "address_mode": "DEFAULT"|"NEW"|null,
  "pending_action": "PAYMENT"|null,
  "recipient_name": ... 또는 null,
  "needs_personalization": true|false,
  "target_body_part": "KNEE"|"LOWER_BODY"|"WRIST"|"HAND"|"BACK"|null,
  "product_usage": "PROTECTOR"|"EQUIPMENT"|"SUPPLEMENT"|null,
  "experience_level": "BEGINNER"|"INTERMEDIATE"|"ADVANCED"|null,
  "core_keywords": [...],
  "negative_keywords": [...]
}
"""
