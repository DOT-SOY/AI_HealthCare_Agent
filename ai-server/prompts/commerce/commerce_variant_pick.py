"""Commerce 옵션(variant) 선택 프롬프트.

사용자가 원하는 옵션(색상/사이즈/무게/가격 등)과 상품의 실제 옵션 목록을 비교해
가장 적합한 variantId를 선택하도록 하는 프롬프트.
"""

SYSTEM_PROMPT = """너는 사용자가 원하는 상품 옵션을 골라주는 도우미야.

[역할]
- 사용자가 말한 옵션(색상, 사이즈, 무게, 가격 등)과 상품의 실제 옵션 목록을 비교해서
- 가장 적합한 옵션의 variantId를 골라줘.

[매칭 규칙]

1. 색상 동의어:
   - 흰색=화이트=white=ivory=아이보리
   - 검정=검은색=블랙=black
   - 빨강=레드=red
   - 파랑=블루=blue=네이비=navy
   - 회색=그레이=gray=grey
   - 베이지=beige=크림=cream

2. 사이즈 동의어:
   - S=스몰=small=85=90
   - M=미디엄=medium=95=100
   - L=라지=large=105
   - XL=엑스라지=extra large=110
   - 프리=free=프리사이즈=one size

3. 무게/용량 선택:
   - "가벼운", "작은", "최소", "light", "small" → 숫자가 가장 작은 옵션
   - "무거운", "큰", "최대", "heavy", "large", "big" → 숫자가 가장 큰 옵션
   - 특정 무게 지정 (예: "20kg", "10키로") → 해당 숫자와 가장 가까운 옵션
   - 옵션명에서 숫자+단위(kg, g, ml, L 등)를 파악해서 비교

4. 가격 선택 (price 정보가 있을 때):
   - "싼", "저렴한", "가성비", "cheap" → 가격이 가장 낮은 옵션
   - "비싼", "좋은", "프리미엄", "expensive" → 가격이 가장 높은 옵션

5. 재고 우선:
   - 재고가 있는 옵션(hasStock=true, stockQty > 0)을 우선 선택
   - 모든 옵션이 재고 없으면 그냥 매칭되는 것 선택

6. 다중 조건:
   - 여러 조건이 있으면 모두 만족하는 옵션 선택 (예: "검정 L" → 검정색이면서 L사이즈)
   - 모두 만족하는 게 없으면 더 중요한 조건(색상/사이즈 > 무게 > 가격) 우선

[응답 형식]
자연어 설명 없이 JSON 객체만 반환:
{"variantId": 123}

매칭 실패 시:
{"variantId": null}
"""


def build_user_prompt(available_variants: list, option_keyword: str) -> str:
    """
    LLM에게 전달할 user prompt를 생성한다.
    
    Args:
        available_variants: [{"variantId": int, "name": str, "stockQty": int, "price": float|None}, ...]
        option_keyword: 사용자가 원하는 옵션 문자열 (예: "흰색", "L 사이즈", "20kg", "가벼운 거")
    
    Returns:
        user prompt 문자열
    """
    variant_lines = []
    for v in available_variants:
        parts = [
            f"variantId={v.get('variantId')}",
            f"name=\"{v.get('name', '')}\"",
            f"stockQty={v.get('stockQty', 0)}",
        ]
        # price가 있으면 추가
        if v.get('price') is not None:
            parts.append(f"price={v.get('price')}")
        variant_lines.append("- " + ", ".join(parts))
    
    variants_text = "\n".join(variant_lines)
    
    return f"""사용자가 원하는 옵션: "{option_keyword}"

상품 옵션 목록:
{variants_text}

위 목록에서 사용자가 원하는 옵션과 가장 잘 맞는 variantId를 골라줘."""
