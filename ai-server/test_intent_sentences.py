"""
의도 분류 테스트: 7개 문장을 OpenAI로 직접 호출해 반환 JSON을 출력.
의존성: openai, python-dotenv (gemini 불필요)
실행: ai-server 디렉터리에서 python test_intent_sentences.py
"""
import os
import sys
import json
from datetime import datetime

# ai-server 루트를 path에 추가
_root = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _root)

from dotenv import load_dotenv
load_dotenv(dotenv_path=os.path.join(_root, ".env"))

from openai import OpenAI

# 프롬프트 파일에서 SYSTEM_PROMPT만 로드 (services 임포트 없이)
with open(os.path.join(_root, "prompts", "intent_classification.py"), "r", encoding="utf-8") as f:
    _content = f.read()
# SYSTEM_PROMPT = """...""" 추출 (첫 """ 이후 끝 """ 전까지)
_start = _content.find('SYSTEM_PROMPT = """') + len('SYSTEM_PROMPT = """')
_end = _content.find('"""', _start)
SYSTEM_PROMPT = _content[_start:_end]

SENTENCES = [
    "나 헬스 처음인데 무슨 보호대가 좋아?",
    "나 다이어트 하는데 음식 추천해주라",
    "좋은 보충제 있어?",
    "데드리프트 할 때 허리가 아픈데 뭐 살지 추천해주라",
    "오일 치 식단 짜줘",
    "지난번에 내가 뭐 샀더라?",
    "아 나 오늘 데드리프트 했는데 허리가 아프다",
]


def classify_intent(text: str) -> dict:
    current_date = datetime.now().strftime("%Y-%m-%d")
    system_prompt = SYSTEM_PROMPT.format(current_date=current_date)
    client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    response = client.chat.completions.create(
        model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": text},
        ],
        temperature=0.0,
        response_format={"type": "json_object"},
    )
    return json.loads(response.choices[0].message.content)


def main():
    print("=== 의도 분류 테스트 (OpenAI 직접 호출) ===\n")
    for i, text in enumerate(SENTENCES, 1):
        print(f"[{i}] 입력: {text}")
        try:
            result = classify_intent(text)
            ai = result.get("ai_answer") or ""
            out = {
                "intent": result.get("intent"),
                "action": result.get("action"),
                "entities": result.get("entities"),
                "ai_answer": (ai[:80] + "...") if len(ai) > 80 else ai,
            }
            print(json.dumps(out, ensure_ascii=False, indent=2))
        except Exception as e:
            print(f"오류: {e}")
        print()
    print("=== 끝 ===")


if __name__ == "__main__":
    # Windows 콘솔 한글 출력
    if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
        sys.stdout.reconfigure(encoding="utf-8")
    main()
