"""
Meal command 서비스
- 사용자 자연어를 구조화된 '식단 작업 명령'으로 변환합니다.
- Pydantic 검증을 통해 enum/날짜/범위를 강제합니다.
"""

from __future__ import annotations

import re
from datetime import date, datetime, timedelta
from typing import Any, Dict, Optional, Literal

from pydantic import BaseModel, Field, field_validator

from services.ai_service import call_meal_ai_json
from prompts.meal_command import SYSTEM_PROMPT


Operation = Literal[
    "GENERATE",
    "GENERATE_OVERWRITE",
    "GENERATE_FILL_MISSING",
    "REPLAN",
    "VISION_ADD",
    "VISION_REPLACE",
    "VISION_CANCEL",
    "MEALTIME_COMPLETE_TOGGLE",
    "MEALTIME_SKIP_TOGGLE",
    "ITEM_COMPLETE_TOGGLE",
    "ITEM_SKIP_TOGGLE",
    "ASK_CLARIFY",
]

GoalType = Literal["DIET", "BULK_UP", "MAINTAIN"]


class MealCommand(BaseModel):
    operation: Operation = "ASK_CLARIFY"
    startDate: Optional[str] = None
    periodDays: Optional[int] = Field(default=None, ge=1, le=90)
    goalType: Optional[GoalType] = None
    targetDate: Optional[str] = None
    mealTime: Optional[Literal["BREAKFAST", "LUNCH", "DINNER"]] = None
    foodName: Optional[str] = None
    alsoReplan: Optional[bool] = None
    clarifyingQuestion: Optional[str] = None
    confidence: Optional[float] = Field(default=None, ge=0.0, le=1.0)

    @field_validator("startDate", "targetDate")
    @classmethod
    def _validate_iso_date(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        s = str(v).strip()
        if not s:
            return None
        # strict YYYY-MM-DD
        datetime.strptime(s, "%Y-%m-%d")
        return s

    @field_validator("clarifyingQuestion")
    @classmethod
    def _normalize_question(cls, v: Optional[str], info) -> Optional[str]:
        if v is None:
            return None
        s = str(v).strip()
        if not s:
            return None
        return s

    @field_validator("operation")
    @classmethod
    def _ask_clarify_requires_question(cls, v: Operation, info) -> Operation:
        # note: we validate after model construction in normalize step (see normalize_command)
        return v


def _today_str() -> str:
    return date.today().isoformat()


def _build_user_prompt(text: str) -> str:
    t = (text or "").strip()
    return f"""현재 날짜: {_today_str()}
사용자 입력: {t}

위 입력을 스키마에 맞는 JSON으로 변환하세요."""


def _norm_text(text: str) -> str:
    return (text or "").strip()


def _nospace_lower(text: str) -> str:
    return re.sub(r"\s+", "", _norm_text(text)).lower()


def _contains_any(haystack: str, needles: list[str]) -> bool:
    return any(n in haystack for n in needles)


def _parse_meal_time(text: str) -> Optional[str]:
    t = _nospace_lower(text)
    if _contains_any(t, ["아침", "조식", "breakfast"]):
        return "BREAKFAST"
    if _contains_any(t, ["점심", "중식", "lunch"]):
        return "LUNCH"
    if _contains_any(t, ["저녁", "석식", "dinner"]):
        return "DINNER"
    return None


def _parse_iso_date_from_text(text: str) -> Optional[str]:
    s = _norm_text(text)
    m = re.search(r"\b(\d{4}-\d{2}-\d{2})\b", s)
    if not m:
        return None
    return m.group(1)


def _parse_start_date_from_text(text: str, default_start_date: Optional[str] = None) -> Optional[str]:
    iso = _parse_iso_date_from_text(text)
    if iso:
        return iso

    t = _nospace_lower(text)
    today = date.today()
    if "오늘" in t:
        return today.isoformat()
    if "내일" in t:
        return (today + timedelta(days=1)).isoformat()
    if "모레" in t:
        return (today + timedelta(days=2)).isoformat()
    if "글피" in t:
        return (today + timedelta(days=3)).isoformat()

    return default_start_date


def _parse_period_days_from_text(text: str) -> Optional[int]:
    raw = _norm_text(text)
    if not raw:
        return None

    t = _nospace_lower(raw)

    # common Korean shortcuts
    if "일주일" in t:
        return 7
    if "한달" in t or "1달" in t or "1개월" in t:
        return 30
    if "반달" in t:
        return 15

    # numeric patterns
    m = re.search(r"(\d+)\s*(일|일치|일간)", raw)
    if m:
        try:
            return int(m.group(1))
        except Exception:
            return None

    m = re.search(r"(\d+)\s*(주|주일|주간)", raw)
    if m:
        try:
            return int(m.group(1)) * 7
        except Exception:
            return None

    m = re.search(r"(\d+)\s*(달|개월)", raw)
    if m:
        try:
            return int(m.group(1)) * 30
        except Exception:
            return None

    return None


def _parse_overlap_strategy_operation(text: str) -> Optional[str]:
    """
    OVERLAP_STRATEGY pending에서 사용자 응답(1/2, 덮어써, 빈날만 등)을 operation으로 변환
    """
    t = _nospace_lower(text)
    if not t:
        return None

    # strict "1"/"2" choice (avoid matching "2주" etc)
    if re.fullmatch(r"1(번|번이요|번요)?[.!]?", t):
        return "GENERATE_OVERWRITE"
    if re.fullmatch(r"2(번|번이요|번요)?[.!]?", t):
        return "GENERATE_FILL_MISSING"

    overwrite_words = ["덮어", "덮어써", "새로", "다시", "리셋", "초기화", "전부", "전체", "삭제"]
    fill_words = ["빈날", "비어", "없는날만", "기존유지", "그대로", "유지", "겹치는날은그대로"]

    if _contains_any(t, overwrite_words):
        return "GENERATE_OVERWRITE"
    if _contains_any(t, fill_words):
        return "GENERATE_FILL_MISSING"
    return None


def _fast_resolve_with_context(text: str, context: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """
    LLM 호출 전, pending/context 기반으로 확실한 후속응답을 규칙 기반으로 빠르게 해석합니다.
    - 목적: 짧은 답변(예: '일주일', '저녁으로 등록해줘')에서 "기억 못함/되묻기"를 제거
    """
    if not context or not isinstance(context, dict):
        return None

    pending = context.get("pending") or {}
    if not isinstance(pending, dict):
        return None

    p_type = str(pending.get("type") or "").strip().upper()
    p_data = pending.get("data") or {}
    if not isinstance(p_data, dict):
        p_data = {}

    t = _norm_text(text)
    t_ns = _nospace_lower(text)

    # 1) Vision follow-up
    if p_type == "VISION_FOLLOWUP":
        meal_time = _parse_meal_time(t)

        cancel_words = ["취소", "아니", "안할", "안해", "그만", "됐어", "됐어요", "필요없", "필요없어"]
        replace_words = ["바꿔", "대체", "변경", "갈아", "바꿔줘", "대체해", "변경해"]
        add_words = ["추가", "기록", "등록", "먹었", "먹었어", "먹었어요", "먹었습니다", "반영", "넣어"]

        also_replan = True if _contains_any(t_ns, ["재정비", "리플랜", "replan"]) else None

        if _contains_any(t_ns, cancel_words):
            return {
                "operation": "VISION_CANCEL",
                "mealTime": meal_time,
                "alsoReplan": also_replan,
                "confidence": 1.0,
            }

        if _contains_any(t_ns, replace_words):
            return {
                "operation": "VISION_REPLACE",
                "mealTime": meal_time,
                "alsoReplan": also_replan,
                "confidence": 1.0,
            }

        if _contains_any(t_ns, ["추가"]):
            return {
                "operation": "VISION_ADD",
                "mealTime": meal_time,
                "alsoReplan": also_replan,
                "confidence": 1.0,
            }

        if _contains_any(t_ns, add_words):
            # 끼니를 명시했으면 '그 끼니로 등록' 뉘앙스가 강하므로 REPLACE를 우선합니다.
            op = "VISION_REPLACE" if meal_time else "VISION_ADD"
            return {
                "operation": op,
                "mealTime": meal_time,
                "alsoReplan": also_replan,
                "confidence": 0.9,
            }

        # 애매하지만 끼니를 지정한 경우(예: "점심이야", "저녁")는 "교체"로 처리하는 편이 UX가 좋습니다.
        # - 물음표가 없고, 사용자가 의사를 명시하지 않은 경우 default를 REPLACE로 둡니다.
        if meal_time:
            return {
                "operation": "VISION_REPLACE",
                "mealTime": meal_time,
                "alsoReplan": also_replan,
                "confidence": 0.8,
            }

        return None

    # 2) ASK_CLARIFY follow-up: period days (e.g. "일주일")
    if p_type == "ASK_CLARIFY":
        need = str(p_data.get("need") or "").strip().upper()
        if need == "PERIOD_DAYS":
            period_days = _parse_period_days_from_text(t)
            if period_days is None:
                return None

            default_start = p_data.get("defaultStartDate")
            if isinstance(default_start, str) and default_start.strip():
                default_start = default_start.strip()
            else:
                default_start = None

            start_date = _parse_start_date_from_text(t, default_start_date=default_start)

            return {
                "operation": "GENERATE",
                "startDate": start_date,
                "periodDays": period_days,
                "confidence": 1.0,
            }

        # need == MEALTIME / FOOD_NAME 은 "원래 의도(토글/비전 등)" 복원이 필요해 규칙 기반 단독처리는 위험.
        return None

    # 3) OVERLAP_STRATEGY follow-up: overwrite vs fill-missing
    if p_type == "OVERLAP_STRATEGY":
        op = _parse_overlap_strategy_operation(t)
        if not op:
            return None

        start_date = p_data.get("startDate")
        start_date = start_date.strip() if isinstance(start_date, str) else None
        period_days = p_data.get("periodDays")
        try:
            period_days = int(period_days) if period_days is not None else None
        except Exception:
            period_days = None

        return {
            "operation": op,
            "startDate": start_date,
            "periodDays": period_days,
            "confidence": 1.0,
        }

    return None


def normalize_command(cmd: MealCommand) -> MealCommand:
    """
    서버측 안전장치:
    - REPLAN인데 targetDate가 없으면 오늘로 보정
    - GENERATE 계열인데 startDate가 없으면 오늘로 보정 (단, periodDays가 없으면 ASK_CLARIFY 우선)
    - ASK_CLARIFY일 때 질문이 비어있으면 기본 질문으로 보정
    """
    if cmd.operation == "REPLAN":
        if not cmd.targetDate:
            cmd.targetDate = _today_str()
        return cmd

    if cmd.operation in ("VISION_ADD", "VISION_REPLACE"):
        if not cmd.targetDate:
            cmd.targetDate = _today_str()
        # mealTime은 사용자가 명시하지 않았으면 null 허용(백엔드가 pending의 defaultMealTime으로 보정 가능)
        return cmd

    if cmd.operation == "VISION_CANCEL":
        return cmd

    if cmd.operation in ("MEALTIME_COMPLETE_TOGGLE", "MEALTIME_SKIP_TOGGLE"):
        if not cmd.targetDate:
            cmd.targetDate = _today_str()
        if not cmd.mealTime:
            cmd.operation = "ASK_CLARIFY"
            cmd.clarifyingQuestion = "어느 끼니를 말씀하셨나요? (아침/점심/저녁 중 선택)"
            return cmd
        return cmd

    if cmd.operation in ("ITEM_COMPLETE_TOGGLE", "ITEM_SKIP_TOGGLE"):
        if not cmd.targetDate:
            cmd.targetDate = _today_str()
        if not cmd.foodName:
            cmd.operation = "ASK_CLARIFY"
            cmd.clarifyingQuestion = "어떤 음식을 완료/생략 처리할까요? (음식명을 말해줘요)"
            return cmd
        return cmd

    if cmd.operation in ("GENERATE", "GENERATE_OVERWRITE", "GENERATE_FILL_MISSING"):
        if cmd.periodDays is None:
            cmd.operation = "ASK_CLARIFY"
            if not cmd.clarifyingQuestion:
                cmd.clarifyingQuestion = (
                    "언제부터, 며칠치 식단을 짜드릴까요?\n"
                    "예: '오늘부터 7일', '내일부터 2주', '2026-02-10부터 30일(한달)'"
                )
            return cmd
        if not cmd.startDate:
            cmd.startDate = _today_str()
        else:
            # 정책: 식단 생성의 시작일은 "오늘 포함 이후"만 허용 (과거 금지)
            try:
                sd = datetime.strptime(cmd.startDate, "%Y-%m-%d").date()
                if sd < date.today():
                    cmd.startDate = _today_str()
            except Exception:
                cmd.startDate = _today_str()
        return cmd

    # ASK_CLARIFY
    if not cmd.clarifyingQuestion:
        cmd.clarifyingQuestion = (
            "원하시는 식단 요청을 이해했어요. 언제부터, 며칠치로 짜드릴까요?\n"
            "예: '오늘부터 7일', '내일부터 2주', '2026-02-10부터 30일(한달)'"
        )
    return cmd


def resolve_meal_command(text: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    LLM + 서버측 검증으로 meal command를 생성합니다.
    실패 시 ASK_CLARIFY로 안전하게 fallback 합니다.
    """
    try:
        # Fast-path: pending 기반 짧은 후속응답은 규칙으로 우선 처리(LLM 불안정/키누락/문맥실패 방지)
        fast = _fast_resolve_with_context(text, context)
        if fast:
            cmd = MealCommand.model_validate(fast)
            cmd = normalize_command(cmd)
            return cmd.model_dump()

        raw = call_meal_ai_json(
            system_prompt=SYSTEM_PROMPT,
            user_prompt=_build_user_prompt_with_context(text, context),
            temperature=0.0,
        )
        cmd = MealCommand.model_validate(raw)
        cmd = normalize_command(cmd)
        return cmd.model_dump()
    except Exception:
        # 엔터프라이즈급 안전장치: 절대 예외를 밖으로 던지지 않고,
        # 프론트/백엔드가 안전하게 다음 턴을 진행할 수 있도록 ASK_CLARIFY로 복구.
        return MealCommand(
            operation="ASK_CLARIFY",
            clarifyingQuestion=(
                "식단 요청을 처리하는 중 일시적인 오류가 발생했어요.\n"
                "기간을 포함해 다시 한 번만 말해줘요. (예: '오늘부터 7일치 식단')"
            ),
            confidence=0.0,
        ).model_dump()


def _build_user_prompt_with_context(text: str, context: Optional[Dict[str, Any]]) -> str:
    """
    context는 Redis에 저장된 meal-only 대화 기록/보류 상태이며, LLM이 후속응답을 해석하는 데 사용됩니다.
    """
    base = _build_user_prompt(text)
    if not context:
        return base
    return (
        base
        + "\n\n[context]\n"
        + "아래 JSON은 식단 도메인에 한정된 최근 대화/보류 상태입니다. 후속 응답 해석에 활용하세요.\n"
        + f"{context}"
    )


