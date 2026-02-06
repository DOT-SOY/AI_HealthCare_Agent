"""
Paddle OCR 기반 인바디 이미지 텍스트 추출 및 파싱
- extract_inbody_from_image(image_bytes) → 프론트에서 기대하는 parsed dict 반환
- OCR 결과 시각화(draw_ocr)는 OCR_OUTPUT_DIR 경로에 저장
- PaddleOCR 2.x/3.x 반환 구조 유연 처리, 박스 기반 같은 행 매칭, 라벨 정규화, 체중조절 4개 전용 파싱
"""
import os
import re
import tempfile
import time
from typing import Dict, Any, Optional, Tuple, List

# OneDNN 비활성화 (ConvertPirAttribute2RuntimeAttribute 미구현 오류 방지)
os.environ["FLAGS_use_mkldnn"] = "0"

from paddleocr import PaddleOCR

# OCR 엔진 싱글톤 (한 번만 초기화)
_ocr: Optional[PaddleOCR] = None

# draw_ocr 결과 저장 경로 (업로드/결과 저장용, 환경변수로 지정 가능)
OCR_OUTPUT_DIR = os.getenv("OCR_OUTPUT_DIR", "ocr_output")

# 디버그 로깅 (환경변수 OCR_DEBUG=1 시 출력)
OCR_DEBUG = os.getenv("OCR_DEBUG", "").strip() == "1"

# OCR_TRACE_LABEL: '신장,체중,골격근량,...' 처럼 콤마로 여러 개 지정 가능
_raw_trace = os.getenv("OCR_TRACE_LABEL", "") or ""
OCR_TRACE_LABELS: List[str] = [s.strip() for s in _raw_trace.split(",") if s.strip()]
OCR_TRACE_WINDOW = int(os.getenv("OCR_TRACE_WINDOW", "10").strip() or "10")


def _get_ocr() -> PaddleOCR:
    global _ocr
    if _ocr is None:
        _ocr = PaddleOCR(use_angle_cls=True, lang='korean', device="cpu")
    return _ocr


def _box_from_rect(rect: Any) -> Any:
    """rec_boxes 형식 [x1,y1,x2,y2] 또는 numpy array → 4점 폴리곤 [[x1,y1],[x2,y1],[x2,y2],[x1,y2]]"""
    try:
        if hasattr(rect, 'tolist'):
            rect = rect.tolist()
        if isinstance(rect, (list, tuple)) and len(rect) >= 4:
            x1, y1 = float(rect[0]), float(rect[1])
            x2, y2 = float(rect[2]), float(rect[3])
            return [[x1, y1], [x2, y1], [x2, y2], [x1, y2]]
    except Exception:
        pass
    return rect


def _normalize_ocr_result(result: Any) -> List[Tuple[Any, str]]:
    """
    PaddleOCR 2.x / 3.x / PaddleX 반환 구조를 [ (box, text), ... ] 형태로 통일.
    - 2.x: result[0] = list of [box, (text, score)]
    - 3.x PaddleX: generator → 첫 요소가 dict { rec_texts: [], rec_boxes: [] 또는 dt_polys }
    """
    out: List[Tuple[Any, str]] = []
    print(f"[paddle_ocr] _normalize_ocr_result: input type={type(result).__name__}", flush=True)
    
    if result is None:
        print("[paddle_ocr] _normalize_ocr_result: result is None", flush=True)
        return out

    original_result = result
    # PaddleX: generator면 첫 페이지만 사용
    if hasattr(result, '__next__') or (hasattr(result, '__iter__') and not isinstance(result, (list, tuple, dict))):
        print("[paddle_ocr] _normalize_ocr_result: detected generator/iterator", flush=True)
        try:
            result = next(iter(result), None)
            print(f"[paddle_ocr] _normalize_ocr_result: generator next() -> type={type(result).__name__}", flush=True)
        except StopIteration:
            print("[paddle_ocr] _normalize_ocr_result: generator StopIteration", flush=True)
            return out
        if result is None:
            print("[paddle_ocr] _normalize_ocr_result: generator returned None", flush=True)
            return out

    # 리스트면 첫 요소가 페이지 데이터
    if isinstance(result, (list, tuple)) and len(result) > 0:
        print(f"[paddle_ocr] _normalize_ocr_result: list/tuple len={len(result)}, taking first", flush=True)
        result = result[0]
        print(f"[paddle_ocr] _normalize_ocr_result: first element type={type(result).__name__}", flush=True)

    # PaddleX dict 또는 속성 기반 객체: rec_texts + rec_boxes / dt_polys
    def _get(obj: Any, key: str, default: Any = None):
        if isinstance(obj, dict):
            return obj.get(key, default)
        return getattr(obj, key, default)

    # Result 객체: .res 또는 .json 에 실제 payload 있음 (공식 문서)
    payload = _get(result, "res", result)
    if payload is result and hasattr(result, "json"):
        print("[paddle_ocr] _normalize_ocr_result: trying .json attribute", flush=True)
        payload = getattr(result, "json", result)
    result = payload

    # 한 단계 더 'res' 래핑된 경우 (출력 형식이 {'res': {...}} 인 경우)
    if isinstance(result, dict) and "res" in result and "rec_texts" not in result:
        print("[paddle_ocr] _normalize_ocr_result: unwrapping nested 'res'", flush=True)
        result = result["res"]

    # PP-StructureV3: overall_ocr_res 안에 rec_texts / rec_boxes 있음
    if isinstance(result, dict) and "overall_ocr_res" in result:
        print("[paddle_ocr] _normalize_ocr_result: found overall_ocr_res (PP-StructureV3)", flush=True)
        result = result["overall_ocr_res"]

    print(f"[paddle_ocr] _normalize_ocr_result: after unwrapping, type={type(result).__name__}", flush=True)
    if isinstance(result, dict):
        keys = list(result.keys())[:30]
        print(f"[paddle_ocr] _normalize_ocr_result: dict keys={keys}", flush=True)

    if isinstance(result, dict) or (hasattr(result, "rec_texts") or hasattr(result, "rec_boxes") or hasattr(result, "dt_polys")):
        texts = _get(result, "rec_texts") or _get(result, "rec_text") or []
        print(f"[paddle_ocr] _normalize_ocr_result: rec_texts type={type(texts).__name__} len={len(texts) if isinstance(texts, (list, tuple)) else 'n/a'}", flush=True)
        if isinstance(texts, str):
            texts = [texts]
        boxes = _get(result, "rec_boxes") or _get(result, "dt_polys") or _get(result, "rec_polys") or []
        if hasattr(boxes, 'tolist'):
            boxes = boxes.tolist()
        if not isinstance(boxes, list):
            boxes = []
        print(f"[paddle_ocr] _normalize_ocr_result: boxes len={len(boxes)}", flush=True)
        if isinstance(texts, (list, tuple)) and len(texts) > 0:
            print(f"[paddle_ocr] _normalize_ocr_result: first few texts: {[str(t)[:50] for t in texts[:5]]}", flush=True)
            # 여러 라벨을 한 번에 추적할 수 있도록 OCR_TRACE_LABELS 사용
            for label in OCR_TRACE_LABELS:
                try:
                    hit = None
                    for idx, t in enumerate(texts):
                        if t and label in str(t):
                            hit = idx
                            break
                    if hit is None:
                        print(f"[paddle_ocr] OCR_TRACE_LABEL='{label}' not found in rec_texts", flush=True)
                        continue
                    start = max(0, hit - OCR_TRACE_WINDOW)
                    end = min(len(texts), hit + OCR_TRACE_WINDOW + 1)
                    print(
                        f"[paddle_ocr] OCR_TRACE_LABEL='{label}' found at index={hit}, "
                        f"showing rec_texts[{start}..{end-1}]",
                        flush=True,
                    )
                    for j in range(start, end):
                        print(f"[paddle_ocr]   rec_texts[{j}] = {texts[j]}", flush=True)
                except Exception as e:
                    print(f"[paddle_ocr] OCR_TRACE_LABEL dump failed for '{label}': {type(e).__name__}: {e}", flush=True)
        for i, text in enumerate(texts):
            if not text:
                continue
            text = str(text).strip()
            box = boxes[i] if i < len(boxes) else None
            if box is not None:
                if hasattr(box, 'tolist'):
                    box = box.tolist()
                box = _box_from_rect(box) if isinstance(box, (list, tuple)) and len(box) == 4 and isinstance(box[0], (int, float)) else box
            if box is not None and text:
                out.append((box, text))
        print(f"[paddle_ocr] _normalize_ocr_result: PaddleX path -> {len(out)} lines", flush=True)
        return out

    if not isinstance(result, list):
        print(f"[paddle_ocr] _normalize_ocr_result: result is not list/dict, type={type(result).__name__}, returning empty", flush=True)
        return out

    # 2.x 스타일: list of [box, (text, score)]
    for item in result:
        box = None
        text = ""
        try:
            if isinstance(item, (list, tuple)) and len(item) >= 2:
                box = item[0]
                second = item[1]
                if isinstance(second, (list, tuple)) and len(second) > 0:
                    text = str(second[0]).strip() if second[0] is not None else ""
                else:
                    text = str(second).strip() if second is not None else ""
            elif isinstance(item, dict):
                box = item.get("box") or item.get("dt_poly") or item.get("points")
                text = (item.get("text") or item.get("rec_text") or "").strip()
            if box is not None and text:
                out.append((box, text))
        except Exception:
            continue
    return out


def _extract_text_lines(image_bytes: bytes) -> Tuple[List[Tuple[Any, str]], Optional[str]]:
    """이미지 바이트에서 Paddle OCR로 텍스트 줄 목록 추출. (lines: [(box, text), ...], temp 경로) 반환."""
    engine = _get_ocr()
    with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as f:
        f.write(image_bytes)
        path = f.name
    try:
        result = engine.predict(path)
        lines = _normalize_ocr_result(result)
        if not lines:
            print("[paddle_ocr] extracted 0 lines from predict()", flush=True)
        else:
            print(f"[paddle_ocr] _extract_text_lines: got {len(lines)} lines", flush=True)
        return lines, path
    except Exception as e:
        print(f"[paddle_ocr] _extract_text_lines EXCEPTION: {type(e).__name__}: {e}", flush=True)
        import traceback
        print(f"[paddle_ocr] traceback: {traceback.format_exc()}", flush=True)
        return [], path


def _box_center(box: Any) -> Tuple[float, float]:
    """박스 [[x1,y1],...] 또는 [x1,y1,x2,y2] → (y_center, x_center)"""
    try:
        if isinstance(box, (list, tuple)) and len(box) >= 4:
            # 평면 4요소 [x1,y1,x2,y2]
            if isinstance(box[0], (int, float)):
                x1, y1, x2, y2 = float(box[0]), float(box[1]), float(box[2]), float(box[3])
                return (y1 + y2) / 2.0, (x1 + x2) / 2.0
            xs = [float(p[0]) for p in box]
            ys = [float(p[1]) for p in box]
            return (min(ys) + max(ys)) / 2.0, (min(xs) + max(xs)) / 2.0
    except Exception:
        pass
    return 0.0, 0.0


def _to_float(s: str) -> Optional[float]:
    """문자열을 float로 변환 (쉼표 소수점 허용)"""
    if not s:
        return None
    s = s.strip().replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def _normalize_label_for_match(s: str) -> str:
    """라벨 매칭용 정규화: 공백 제거, 괄호·단위 제거"""
    if not s:
        return ""
    s = re.sub(r'\s+', '', s)
    s = re.sub(r'\([^)]*\)', '', s)  # (L), (kg) 등 제거
    s = re.sub(r'[%kgLcm]', '', s, flags=re.I)
    return s.strip()


def _parse_number_after_label(full_text: str, label: str) -> Optional[float]:
    """'라벨 70.5', '라벨 70.5 kg', '라벨 : 70,5' 등 형태로 숫자 추출"""
    # 라벨 정규화 버전도 시도 (괄호 제거한 라벨로 검색)
    labels_to_try = [label, re.sub(r'\([^)]*\)', '', label).strip()]
    for lbl in labels_to_try:
        if not lbl:
            continue
        patterns = [
            re.escape(lbl) + r'\s*[:\s]*(?:kg|%|L|cm)?\s*([-]?\d+[.,]?\d*)',
            re.escape(lbl) + r'\s*([-]?\d+[.,]?\d*)\s*(?:kg|%|L|cm)?',
        ]
        for pattern in patterns:
            m = re.search(pattern, full_text)
            if m:
                val = _to_float(m.group(1))
                if val is not None:
                    return val
    return None


def _parse_number_before_label(full_text: str, label: str) -> Optional[float]:
    """'70.5 kg 체중', '73 체중' 등 숫자가 라벨 앞에 오는 형태"""
    labels_to_try = [label, re.sub(r'\([^)]*\)', '', label).strip()]
    for lbl in labels_to_try:
        if not lbl:
            continue
        patterns = [
            r'([-]?\d+[.,]?\d*)\s*(?:kg|%|L|cm)?\s*' + re.escape(lbl),
            r'([-]?\d+[.,]?\d*)\s*' + re.escape(lbl),
        ]
        for pattern in patterns:
            m = re.search(pattern, full_text)
            if m:
                val = _to_float(m.group(1))
                if val is not None:
                    return val
    return None


def _is_mostly_number(s: str) -> bool:
    """문자열이 숫자·소수점·공백 위주인지 (인접 줄 매칭용)"""
    s = s.strip()
    if not s:
        return False
    cleaned = re.sub(r'[\d\s.,\-%]', '', s)
    return len(cleaned) <= 2  # kg, L 등 단위 허용


def _line_matches_label(ln: str, lbl: str) -> bool:
    """현재 줄이 라벨과 일치하는지 (공백 제거, 괄호·단위 무시)"""
    n_ln = _normalize_label_for_match(ln)
    n_lbl = _normalize_label_for_match(lbl)
    if not n_lbl:
        return False
    if n_ln == n_lbl:
        return True
    # 라벨이 줄 앞부분에 포함된 경우 (예: "체수분(L)" → "체수분")
    return n_ln.startswith(n_lbl) or n_lbl.startswith(n_ln)


def _extract_number_from_string(s: str) -> Optional[float]:
    """문자열에서 첫 번째 숫자(소수 포함, 음수 허용) 추출"""
    m = re.search(r'([-]?\d+[.,]?\d*)', s)
    if m:
        return _to_float(m.group(1))
    return None


def _save_draw_ocr_result(image_path: str, lines: List[Tuple[Any, str]]) -> None:
    """OCR 결과를 이미지에 그려 OCR_OUTPUT_DIR 경로에 저장."""
    os.makedirs(OCR_OUTPUT_DIR, exist_ok=True)
    try:
        from PIL import Image, ImageDraw
        image = Image.open(image_path).convert("RGB")
        draw = ImageDraw.Draw(image)
        for box, txt in lines:
            try:
                xs = [p[0] for p in box]
                ys = [p[1] for p in box]
                xmin, xmax = min(xs), max(xs)
                ymin, ymax = min(ys), max(ys)
                draw.rectangle([xmin, ymin, xmax, ymax], outline="red", width=2)
                if txt:
                    draw.text((xmin, max(0, ymin - 14)), txt[:32], fill="red")
            except Exception:
                pass
        out_name = f"ocr_result_{int(time.time() * 1000)}.jpg"
        out_path = os.path.join(OCR_OUTPUT_DIR, out_name)
        image.save(out_path)
    except Exception as e:
        print(f"[paddle_ocr] draw save failed: {e}")
    finally:
        try:
            os.unlink(image_path)
        except Exception:
            pass


# 라벨 → parsed 키 매핑 (라벨 변형 포함)
LABEL_MAP = [
    ('체중', 'weight'),
    ('키', 'height'),
    ('신장', 'height'),
    ('골격근량', 'skeletalMuscleMass'),
    ('골격 근량', 'skeletalMuscleMass'),
    ('체지방률', 'bodyFatPercent'),
    ('체지방 률', 'bodyFatPercent'),
    ('체수분', 'bodyWater'),
    ('체수 분', 'bodyWater'),
    ('체수분(L)', 'bodyWater'),
    ('단백질', 'protein'),
    ('단백 질', 'protein'),
    ('무기질', 'minerals'),
    ('무기 질', 'minerals'),
    ('체지방량', 'bodyFatMass'),
    ('체지방 량', 'bodyFatMass'),
    ('체지방', 'bodyFatMass'),
    ('적정체중', 'targetWeight'),
    ('적정 체중', 'targetWeight'),
    ('체중조절', 'weightControl'),
    ('체중 조절', 'weightControl'),
    ('지방조절', 'fatControl'),
    ('지방 조절', 'fatControl'),
    ('근육조절', 'muscleControl'),
    ('근육 조절', 'muscleControl'),
]

def _in_range(key: str, val: float) -> bool:
    """인바디 수치의 대략적인 합리 범위 필터 (오매칭 숫자 제거용)."""
    # (min, max) — 단위는 프론트 표시 기준(cm, kg, L, %)
    ranges = {
        "height": (120.0, 220.0),
        "weight": (30.0, 200.0),
        "skeletalMuscleMass": (10.0, 60.0),
        "bodyFatPercent": (3.0, 60.0),
        "bodyWater": (20.0, 70.0),
        "protein": (5.0, 25.0),
        "minerals": (1.0, 6.0),
        "bodyFatMass": (1.0, 80.0),
        "targetWeight": (30.0, 200.0),
        # 조절값은 음수 가능
        "weightControl": (-50.0, 50.0),
        "fatControl": (-50.0, 50.0),
        "muscleControl": (-50.0, 50.0),
    }
    r = ranges.get(key)
    if r is None:
        return True
    lo, hi = r
    return lo <= val <= hi


def _fix_body_composition_relations(parsed: Dict[str, Any]) -> None:
    """
    체수분/단백질/무기질/체지방량/체중 다섯 값은
    bodyWater + protein + minerals + bodyFatMass ≈ weight
    관계를 이용해 잘못 매핑된 값을 교정한다.
    """
    # 모든 parsed 값 중 1~100 사이의 양수만 후보로 사용 (키/조절값 등 제외)
    candidates: List[float] = []
    for v in parsed.values():
        if isinstance(v, (int, float)):
            fv = float(v)
            if 1.0 <= fv <= 100.0:
                candidates.append(fv)
    # 중복 제거
    candidates = sorted(set(candidates))
    if len(candidates) < 5:
        return

    import itertools

    best_combo = None
    best_weight_val = None
    best_diff = float("inf")

    # 5개 숫자 조합 중에서, 하나가 나머지 4개 합과 가장 가까운 조합을 찾는다.
    for combo in itertools.combinations(candidates, 5):
        for i in range(5):
            weight_val = combo[i]
            others = [combo[j] for j in range(5) if j != i]
            s = sum(others)
            diff = abs(weight_val - s)
            if diff < best_diff:
                best_diff = diff
                best_combo = combo
                best_weight_val = weight_val

    # 차이가 너무 크면(예: 2kg 이상) 신뢰하지 않는다.
    if best_combo is None or best_weight_val is None or best_diff > 2.0:
        return

    combo_vals = list(best_combo)
    # weight 값 확정
    weight_val = best_weight_val
    rest_vals = [v for v in combo_vals if v != weight_val] or combo_vals[:-1]

    # 나머지 4개: 대략적인 크기 순으로 역할 추정
    # - bodyWater: 보통 30~70 중 가장 큰 값
    # - minerals: 보통 1~6 정도로 가장 작은 값
    # - protein: 그 다음 작은 값(5~25)
    # - bodyFatMass: 나머지 하나
    rest_vals_sorted = sorted(rest_vals)
    minerals_val = rest_vals_sorted[0]
    if len(rest_vals_sorted) >= 3:
        protein_val = rest_vals_sorted[1]
        bodyFatMass_val = rest_vals_sorted[2] if len(rest_vals_sorted) >= 3 else rest_vals_sorted[-1]
    else:
        # 값이 부족하면 매핑을 수행하지 않는다.
        return
    bodyWater_candidates = [v for v in rest_vals if v not in (minerals_val, protein_val, bodyFatMass_val)]
    if bodyWater_candidates:
        bodyWater_val = max(bodyWater_candidates)
    else:
        bodyWater_val = max(rest_vals)

    # 교정된 값 덮어쓰기
    parsed["weight"] = weight_val
    parsed["bodyWater"] = bodyWater_val
    parsed["protein"] = protein_val
    parsed["minerals"] = minerals_val
    parsed["bodyFatMass"] = bodyFatMass_val


def _parse_by_box_proximity(
    items: List[Tuple[float, float, str, Any]],
    parsed: Dict[str, Any],
    row_threshold: float = 25.0,
) -> None:
    """
    라벨별로 같은 행(Y 근접)에 있는 숫자 중 가까운 것 하나만 매칭.
    숫자 하나를 여러 라벨에 쓰지 않도록, 한 번 매칭된 숫자 항목(인덱스)은 제외.
    """
    used_num_indices: set = set()  # 이미 어떤 키에 매칭된 숫자 항목 인덱스
    for label, key in LABEL_MAP:
        if key in parsed:
            continue
        label_items = [(y2, x2) for (y2, x2, text2, _) in items if _line_matches_label(text2, label)]
        if not label_items:
            continue
        best_val: Optional[float] = None
        best_dist = float("inf")
        best_i: Optional[int] = None
        for y_label, x_label in label_items:
            for i, (y_num, x_num, text_num, _) in enumerate(items):
                if i in used_num_indices:
                    continue
                # 인바디 표는 보통 라벨 왼쪽, 값 오른쪽 → 오른쪽 값만 후보
                if x_num <= x_label:
                    continue
                if abs(y_num - y_label) > row_threshold:
                    continue
                num_val = _extract_number_from_string(text_num.strip())
                if num_val is None:
                    continue
                # 키별 합리 범위 필터
                if not _in_range(key, float(num_val)):
                    continue
                dist = abs(x_num - x_label)
                if dist < best_dist:
                    best_dist = dist
                    best_val = num_val
                    best_i = i
        if best_val is not None and best_i is not None:
            parsed[key] = best_val
            used_num_indices.add(best_i)


def _parse_weight_control_block(
    line_texts: List[str],
    items: List[Tuple[float, float, str, Any]],
    parsed: Dict[str, Any],
) -> None:
    """
    '체중조절' / '적정체중' 등이 나오는 구간을 찾아, 그 근처 행에서 순서대로 4개 숫자를
    targetWeight, weightControl, fatControl, muscleControl에 매핑.
    """
    control_keys = ['targetWeight', 'weightControl', 'fatControl', 'muscleControl']
    if all(k in parsed for k in control_keys):
        return
    # 체중조절 관련 라벨이 있는 행의 Y 수집
    label_y_values: List[float] = []
    for label, key in [('적정체중', 'targetWeight'), ('체중조절', 'weightControl'),
                        ('지방조절', 'fatControl'), ('근육조절', 'muscleControl')]:
        for y, x, text, _ in items:
            if _line_matches_label(text, label):
                label_y_values.append(y)
                break
    if not label_y_values:
        return
    y_min = min(label_y_values) - 30
    y_max = max(label_y_values) + 30
    # 해당 Y 범위 안의 (y, x, text) 중 숫자만 추출, y then x 정렬
    candidates: List[Tuple[float, float, float, str]] = []
    for y, x, text, _ in items:
        if y_min <= y <= y_max:
            val = _extract_number_from_string(text)
            if val is not None:
                candidates.append((y, x, val, text))
    candidates.sort(key=lambda t: (t[0], t[1]))
    # 체중조절 4개: 보통 적정체중, 체중조절, 지방조절, 근육조절 순.
    # 1) targetWeight는 이미 확정된 체성분 값들과 weight를 활용해 별도로 선정
    used_body_vals = set()
    for k in ["bodyWater", "protein", "minerals", "bodyFatMass"]:
        v = parsed.get(k)
        if isinstance(v, (int, float)):
            used_body_vals.add(float(v))
    weight_val = parsed.get("weight")

    # targetWeight 후보: 양수이면서 targetWeight 범위 통과, 이미 사용된 체성분 값은 제외
    if "targetWeight" not in parsed and weight_val is not None:
        positive_targets: List[Tuple[int, float]] = []
        for idx, (y, x, val, text) in enumerate(candidates):
            if val <= 0:
                continue
            if not _in_range("targetWeight", float(val)):
                continue
            if float(val) in used_body_vals:
                continue
            positive_targets.append((idx, float(val)))
        if positive_targets:
            # 체중과 가장 가까운 값을 targetWeight로 선택
            best_idx, best_val = min(
                positive_targets,
                key=lambda t: abs(t[1] - float(weight_val))
            )
            parsed["targetWeight"] = best_val
            # 이후 다른 조절값 배정에서 재사용되지 않도록 후보에서 제거
            candidates = [c for i, c in enumerate(candidates) if i != best_idx]

    # 2) 남은 후보들로 weightControl, fatControl, muscleControl 배정
    if candidates:
        for idx, key in enumerate(control_keys):
            if key in parsed or key == "targetWeight" or idx >= len(candidates):
                continue
            val = candidates[idx][2]
            if _in_range(key, float(val)):
                parsed[key] = val


def _parse_measurement_date(all_text: str) -> Optional[str]:
    """검사일시, 측정일 등 다양한 날짜 형식 → YYYY-MM-DD"""
    # 검사일시 2025.01.30. 14:28 / 2025.01.30
    m = re.search(r'검사일시\s*[:\s]*(\d{4})[.\-](\d{1,2})[.\-](\d{1,2})', all_text)
    if m:
        y, mo, d = m.group(1), m.group(2).zfill(2), m.group(3).zfill(2)
        return f"{y}-{mo}-{d}"
    m = re.search(r'측정일\s*[:\s]*(\d{4})[.\-](\d{1,2})[.\-](\d{1,2})', all_text)
    if m:
        y, mo, d = m.group(1), m.group(2).zfill(2), m.group(3).zfill(2)
        return f"{y}-{mo}-{d}"
    for pat in [
        r'(\d{4})[-./](\d{1,2})[-./](\d{1,2})',
        r'(\d{2})[-./](\d{1,2})[-./](\d{1,2})',
    ]:
        m = re.search(pat, all_text)
        if m:
            g = m.groups()
            y, mo, d = g[0], g[1].zfill(2), g[2].zfill(2)
            if len(y) == 2:
                y = '20' + y
            return f"{y}-{mo}-{d}".rstrip('-')
    return None


def extract_inbody_from_image(image_bytes: bytes) -> Dict[str, Any]:
    """
    인바디 이미지 바이트에서 체성분 수치 추출.
    반환 형식: 프론트 ProfileIndex / ocrApi 기대 형태
    { weight, height, skeletalMuscleMass, bodyFatPercent, bodyWater, protein, minerals, bodyFatMass,
      targetWeight?, weightControl?, fatControl?, muscleControl?, measurementDate? }
    """
    print(f"[paddle_ocr] extract_inbody_from_image called, image_bytes len={len(image_bytes)}", flush=True)
    try:
        lines, temp_path = _extract_text_lines(image_bytes)
    except Exception as e:
        print(f"[paddle_ocr] extract_inbody EXCEPTION in _extract_text_lines: {type(e).__name__}: {e}", flush=True)
        import traceback
        print(f"[paddle_ocr] traceback: {traceback.format_exc()}", flush=True)
        return {}
    if not lines:
        print(f"[paddle_ocr] extract_inbody: lines=0, returning empty dict", flush=True)
        if temp_path:
            try:
                os.unlink(temp_path)
            except Exception:
                pass
        return {}
    
    print(f"[paddle_ocr] extract_inbody: lines={len(lines)} extracted, starting parsing", flush=True)

    # (y_center, x_center, text, box) 리스트
    items: List[Tuple[float, float, str, Any]] = []
    for box, text in lines:
        yc, xc = _box_center(box)
        items.append((yc, xc, text, box))

    all_text = ' '.join([t for _, _, t, _ in items])
    line_texts = [t.strip() for _, _, t, _ in items]

    parsed: Dict[str, Any] = {}

    # 0-헤더 전용 규칙: 상단 영역에서 신장(키) 우선 확정
    # 예: "신장 173cm", "신장: 173 cm" 등
    header_height = None
    m = re.search(r'(?:신장|키)\s*[:\s]*([12]\d{2})\s*cm', all_text)
    if not m:
        # OCR이 "신장 ... 173cm"처럼 떨어져 있을 때를 위한 느슨한 패턴
        m = re.search(r'(?:신장|키)[^0-9]{0,10}([12]\d{2})\s*cm', all_text)
    if m:
        h_val = _to_float(m.group(1))
        if h_val is not None and _in_range("height", h_val):
            parsed["height"] = h_val

    # 0-표 영역 한정: "체성분분석" 블록 아래만 체성분 표로 사용
    table_items = items
    table_y_min: Optional[float] = None
    table_y_max: Optional[float] = None
    for y, x, text, _ in items:
        if "체성분분석" in text or "Body Composition Analysis" in text:
            table_y_min = y + 10.0    # 제목 바로 아래부터
            table_y_max = y + 400.0   # 인바디370S 기준 대략적인 범위
            break
    if table_y_min is not None and table_y_max is not None:
        table_items = [(y, x, t, b) for (y, x, t, b) in items if table_y_min <= y <= table_y_max]
        if OCR_DEBUG:
            print(f"[paddle_ocr] table_y_range=({table_y_min},{table_y_max}), table_items={len(table_items)}", flush=True)

    # 1) 박스 기반 같은 행 매칭 (라벨과 숫자가 같은 행에 있을 때)
    # 인바디 표는 행 간격이 촘촘해서 Y 허용오차를 너무 키우면 오매칭이 급증함
    _parse_by_box_proximity(table_items, parsed, row_threshold=12.0)

    # 2) 전체 텍스트에서 "라벨 + 숫자" 패턴
    for label, key in LABEL_MAP:
        if key in parsed:
            continue
        val = _parse_number_after_label(all_text, label)
        if val is not None:
            parsed[key] = val

    # 3) "숫자 + 라벨" 패턴
    for label, key in LABEL_MAP:
        if key in parsed:
            continue
        val = _parse_number_before_label(all_text, label)
        if val is not None:
            parsed[key] = val

    # 4) 줄 단위 시도
    for line in line_texts:
        for label, key in LABEL_MAP:
            if key in parsed:
                continue
            val = _parse_number_after_label(line, label) or _parse_number_before_label(line, label)
            if val is not None:
                parsed[key] = val

    # 5) 인접 줄 매칭 (라벨 한 줄 + 숫자 한 줄)
    for i, line in enumerate(line_texts):
        if i + 1 >= len(line_texts):
            break
        next_line = line_texts[i + 1]
        if not _is_mostly_number(next_line):
            continue
        num_val = _extract_number_from_string(next_line)
        if num_val is None:
            continue
        for label, key in LABEL_MAP:
            if key in parsed:
                continue
            if _line_matches_label(line, label):
                parsed[key] = num_val
                break

    # 6) 체중조절 4개 전용 블록 파싱
    _parse_weight_control_block(line_texts, items, parsed)

    # 6.5) 체성분 블록(체수분/단백질/무기질/체지방량/체중) 관계 기반 교정
    _fix_body_composition_relations(parsed)

    # 7) 측정일
    date_str = _parse_measurement_date(all_text)
    if date_str:
        parsed['measurementDate'] = date_str.rstrip('-')

    # 최종 안전장치: 키별 합리 범위 밖 값은 제거 (오매칭 방지)
    for k in list(parsed.keys()):
        v = parsed.get(k)
        if isinstance(v, (int, float)) and not _in_range(k, float(v)):
            parsed.pop(k, None)

    # parsed가 비면 원인 추적용 로그 (OCR 텍스트는 있는데 매칭 실패)
    # measurementDate만 있어도 parsed는 비어있지 않으므로, 숫자 필드만 확인
    numeric_keys = ['weight', 'height', 'skeletalMuscleMass', 'bodyFatPercent', 'bodyWater', 
                   'protein', 'minerals', 'bodyFatMass', 'targetWeight', 'weightControl', 
                   'fatControl', 'muscleControl']
    has_numeric = any(k in parsed for k in numeric_keys)
    
    if not has_numeric:
        print(f"[paddle_ocr] parsed empty (no numeric fields): lines={len(lines)} all_text_len={len(all_text)} parsed_keys={list(parsed.keys())}", flush=True)
        if all_text:
            # OCR 텍스트 샘플 (라벨이 어떻게 인식됐는지 확인용)
            sample = all_text[:500] if len(all_text) > 500 else all_text
            print(f"[paddle_ocr] OCR text sample: {sample}", flush=True)
            # 라벨 매칭 시도: LABEL_MAP의 라벨이 실제 텍스트에 있는지 확인
            found_labels = []
            for label, _ in LABEL_MAP:
                if label in all_text or _normalize_label_for_match(label) in _normalize_label_for_match(all_text):
                    found_labels.append(label)
            if found_labels:
                print(f"[paddle_ocr] found labels in text: {found_labels[:10]}", flush=True)
            else:
                print(f"[paddle_ocr] no labels from LABEL_MAP found in OCR text", flush=True)
            # 숫자 추출 테스트
            nums = []
            for i, (y, x, text, _) in enumerate(items[:20]):  # 처음 20개만
                val = _extract_number_from_string(text.strip())
                if val is not None:
                    nums.append(f"{text.strip()}->{val}")
            if nums:
                print(f"[paddle_ocr] extracted numbers (first 20): {nums}", flush=True)
            else:
                print(f"[paddle_ocr] no numbers found in first 20 items", flush=True)

    if OCR_DEBUG:
        print(f"[paddle_ocr] lines={len(lines)} all_text_len={len(all_text)} parsed_keys={list(parsed.keys())}")
        if len(lines) <= 3 and all_text:
            print(f"[paddle_ocr] all_text sample: {all_text[:200]}...")

    if temp_path:
        _save_draw_ocr_result(temp_path, lines)

    return parsed
