"""
인바디(Inbody) OCR 서비스
- GPT-4o mini (Vision) 기반으로 이미지에서 주요 수치를 추출합니다.
- 정확도 향상을 위해 이미지를 상단/하단으로 분할하여 각각 분석한 뒤 병합합니다.
"""
from typing import Dict, Any, Optional
import base64
import json
import re
from openai import AsyncOpenAI
import os
import logging
import asyncio
from io import BytesIO
from PIL import Image, ImageOps, ImageEnhance

# 로거 설정
logger = logging.getLogger(__name__)

# 성능/정확도 튜닝 파라미터 (환경변수로 조정 가능)
OCR_OPENAI_MODEL = os.getenv("OCR_OPENAI_MODEL", "gpt-4o-mini")
# 사용자 요청 스펙(정확도 우선)
OCR_VISION_DETAIL = os.getenv("OCR_VISION_DETAIL", "high")  # high/low/auto (지원 범위 내)
# 정확도 우선:
# - 원본 전체 이미지를 먼저 죽이지 않기 위해 기본값은 "리사이즈 안함(0)"으로 둡니다.
# - 대신 각 크롭 이미지가 너무 큰 경우만 OCR_CROP_MAX_WIDTH로 제한합니다.
OCR_MAX_WIDTH = int(os.getenv("OCR_MAX_WIDTH", "0"))  # 0이면 원본 리사이즈 안함
OCR_CROP_MAX_WIDTH = int(os.getenv("OCR_CROP_MAX_WIDTH", "2800"))
OCR_JPEG_QUALITY = int(os.getenv("OCR_JPEG_QUALITY", "92"))
OCR_CONTRAST = float(os.getenv("OCR_CONTRAST", "1.15"))  # 1.0이면 미적용
OCR_SHARPNESS = float(os.getenv("OCR_SHARPNESS", "1.2"))  # 1.0이면 미적용
# 체수분(bodyWater)만 정확도 보강: 초타이트 크롭을 Top 호출에 추가(호출 수 증가 없음)
OCR_BODY_WATER_TIGHT_CROP = os.getenv("OCR_BODY_WATER_TIGHT_CROP", "1").strip().lower() not in ("0", "false", "no")
OCR_BODY_WATER_TARGET_WIDTH = int(os.getenv("OCR_BODY_WATER_TARGET_WIDTH", "1200"))

# -------------------------------------------------------------------------
# 프롬프트 정의 (상단용 / 하단용 분리 + 사용자 지정 순서 반영)
# -------------------------------------------------------------------------

SYSTEM_PROMPT_TOP = """
너는 인바디(InBody) 결과지를 분석하는 OCR 전문가야.
사용자가 표시한 **1~3번 구역**을 순서대로 확인해서 아래 JSON 키들만 반환해.

입력 이미지:
- 이미지 1: 상단(1~3) 전체 구역
- 이미지 2: 체성분 분석 표의 **체수분(bodyWater) 측정치 숫자만** 초타이트 크롭

중요:
- bodyWater는 **반드시 이미지 2에서만** 읽어.
- bodyWater 외 나머지 값은 **이미지 1에서만** 읽어.

[1번: 상단 정보(Header)]
- height: 신장/키
- measuredDate: 검사일시 (YYYY.MM.DD HH:mm)

[2번: 체성분 분석(Body Composition Analysis) 표]
- bodyWater: 체수분
- protein: 단백질
- minerals: 무기질
- bodyFatMass: 체지방량

중요:
- (표준범위)처럼 괄호로 표시된 범위값은 절대 쓰지 마.
- **신체변화(Body Composition History)** 그래프/추세선에 있는 숫자는 절대 쓰지 마.
- **반드시 표 안의 '측정치' 숫자**만 사용해.
- '(숫자~숫자)' 같이 물결표(~)가 포함된 범위값은 절대 쓰지 마.
- 괄호 '(' ')' 가 보이면 그 안의 숫자는 전부 범위값이니 무시하고, 괄호 밖(측정치)만 써.

[3번: 골격근·지방 분석(Muscle-Fat Analysis)]
- weight: 체중
- skeletalMuscleMass: 골격근량

규칙:
1) 단위(kg, cm, %, L)는 제거하고 숫자만(검사일시는 문자열).
3) 안 보이면 null.
4) JSON 형식 외에는 아무 말도 하지 마.
"""

SYSTEM_PROMPT_BOTTOM = """
너는 인바디(InBody) 결과지의 **4~5번 구역(비만진단/체중조절)**을 분석하는 OCR 전문가야.
사용자가 표시한 순서대로 필요한 값만 JSON으로 반환해.

[4번: 비만 진단(Obesity Analysis)]
- bodyFatPercent: 체지방률(%)

[5번: 체중 조절(Weight Control)]
- targetWeight: 적정체중
- weightControl: 체중조절 (부호 +,- 포함)
- fatControl: 지방조절 (부호 +,- 포함)
- muscleControl: 근육조절 (부호 +,- 포함, 0.0일 수도 있음)

규칙:
1) 단위 제거하고 숫자만.
2) 안 보이면 null.
3) JSON 형식 외에는 아무 말도 하지 마.
"""

SYSTEM_PROMPT_BODYCOMP_MICRO = """
너는 인바디(InBody) 결과지의 '체성분 분석(Body Composition Analysis)' 표에서
특히 **단백질(Protein)**과 **무기질(Minerals)**만 정확히 읽는 OCR 전문가야.
이 이미지는 결과지의 **좌측 상단 표 부분만 잘라낸 이미지**다.

다음 2개 값만 JSON으로 반환해:
- protein: 단백질
- minerals: 무기질

규칙:
- 괄호 안의 표준범위는 절대 쓰지 마.
- 행 라벨(단백질/무기질) 옆에 있는 **본인 측정치**만 써.
- 단위는 제거하고 숫자만.
- 안 보이면 null.
- JSON 형식 외에는 아무 말도 하지 마.
"""

SYSTEM_PROMPT_WEIGHT_CONTROL = """
너는 인바디(InBody) 결과지의 '체중조절(Weight Control)' 섹션만 추출하는 OCR 전문가야.
이 이미지는 결과지의 **우측 하단 체중조절 부분만 잘라낸 이미지**다.

아래 4개 값만 '본인 측정값'으로 찾아서 JSON으로 반환해:
- targetWeight: 적정체중
- weightControl: 체중조절 (부호 +,- 포함)
- fatControl: 지방조절 (부호 +,- 포함)
- muscleControl: 근육조절 (부호 +,- 포함, 0.0일 수도 있음)

규칙:
- 단위(kg)는 제거하고 숫자만.
- 값이 안 보이면 null.
- JSON 형식 외에는 아무 말도 하지 마.
"""

async def analyze_inbody_image(image_bytes: bytes) -> Dict[str, Any]:
    """
    정확도 우선:
    - 원본 전체를 먼저 리사이즈하지 않고(기본), 상단/하단/마이크로 크롭을 만든 뒤
    - 각 크롭이 너무 큰 경우만 OCR_CROP_MAX_WIDTH로 제한하여 Vision 호출
    """
    try:
        logger.info("[OCR] Starting Inbody Analysis (Crop+Resize Mode)...")
        
        # 1. API Key 확인
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            return {"error": "API Key is missing"}
        
        client = AsyncOpenAI(api_key=api_key)

        # 2. 이미지 로드 + 전처리(회전/리사이즈/대비/샤프닝)
        try:
            full_image = _load_and_prepare_image(image_bytes)
            w, h = full_image.size

            # 사용자 지정(1~5) 구역에 맞춰 상단/하단 분리:
            # - 상단(1~3): 헤더 + 체성분 + 골격근·지방 분석
            # - 하단(4~5): 비만진단 + 체중조절
            top_img = full_image.crop((0, 0, w, int(h * 0.60)))
            bottom_img = full_image.crop((0, int(h * 0.50), w, h))

            # 읽기 쉬움 업스케일 (너무 크게 올리면 느려지므로 적당히)
            top_img = _downscale_if_needed(top_img, OCR_CROP_MAX_WIDTH)
            bottom_img = _downscale_if_needed(bottom_img, OCR_CROP_MAX_WIDTH)
            top_img = _upscale_for_readability(top_img, target_width=1600)
            bottom_img = _upscale_for_readability(bottom_img, target_width=1600)

            # 체중조절(우측) 전용 크롭 (하단에서 더 타이트하게)
            wc_img = full_image.crop((int(w * 0.52), int(h * 0.42), w, int(h * 0.78)))
            wc_img = _downscale_if_needed(wc_img, OCR_CROP_MAX_WIDTH)
            wc_img = _upscale_for_readability(wc_img, target_width=1400)

            # 단백질/무기질(체성분 표) 미세 영역(필요시만 fallback)
            bc_img = full_image.crop((0, int(h * 0.10), int(w * 0.62), int(h * 0.42)))
            bc_img = _downscale_if_needed(bc_img, OCR_CROP_MAX_WIDTH)
            bc_img = _upscale_for_readability(bc_img, target_width=1400)

            # 체중/골격근량/체지방량(골격근·지방 분석) 미세 영역 fallback
            mf_img = full_image.crop((0, int(h * 0.28), int(w * 0.70), int(h * 0.55)))
            mf_img = _downscale_if_needed(mf_img, OCR_CROP_MAX_WIDTH)
            mf_img = _upscale_for_readability(mf_img, target_width=1600)

            # 비만진단(체지방률) 미세 영역 fallback
            ob_img = full_image.crop((0, int(h * 0.50), int(w * 0.60), int(h * 0.70)))
            ob_img = _downscale_if_needed(ob_img, OCR_CROP_MAX_WIDTH)
            ob_img = _upscale_for_readability(ob_img, target_width=1400)

            # 각각 Base64 변환
            top_b64 = _image_to_base64(top_img)
            bottom_b64 = _image_to_base64(bottom_img)
            wc_b64 = _image_to_base64(wc_img)
            bc_b64 = _image_to_base64(bc_img)
            mf_b64 = _image_to_base64(mf_img)
            ob_b64 = _image_to_base64(ob_img)

            # 체수분(bodyWater) 초타이트 크롭(범위(~)가 보이지 않도록 숫자 칸 위주)
            bw_b64 = None
            if OCR_BODY_WATER_TIGHT_CROP:
                try:
                    # bodyWater는 대비/샤프닝 과적용이 오독(2↔3 등)을 만들 수 있어,
                    # 전체 전처리(full_image)와 별개로 "최소 전처리" 이미지에서 잘라냅니다.
                    bw_src = _load_image_minimal(image_bytes)
                    bw_img = _crop_body_water_tight(bw_src)
                    bw_img = _downscale_if_needed(bw_img, OCR_CROP_MAX_WIDTH)
                    bw_img = _upscale_for_readability_soft(bw_img, target_width=OCR_BODY_WATER_TARGET_WIDTH)
                    bw_b64 = _image_to_base64_png(bw_img)
                except Exception as e:
                    logger.warning(f"[OCR] bodyWater tight crop failed. fallback to top result. err={e}")

            logger.info(f"[OCR] Crops ready. top={len(top_b64)}b bottom={len(bottom_b64)}b wc={len(wc_b64)}b")
            
        except Exception as e:
            logger.error(f"[OCR] Image Processing Failed: {e}")
            return {"error": "이미지 처리 중 오류가 발생했습니다."}

        # 3. 병렬 요청 (상단/하단/체중조절 동시 실행)
        if bw_b64:
            task_top = _call_gpt_vision_multi(
                client,
                images=[
                    {"b64": top_b64, "mime": "image/jpeg", "hint": "이미지 1 (TopCrop): 상단(1~3) 전체 구역"},
                    {"b64": bw_b64, "mime": "image/png", "hint": "이미지 2 (BodyWaterCrop): 체수분 측정치 숫자만(괄호/범위값/그래프 없음)"},
                ],
                system_prompt=SYSTEM_PROMPT_TOP,
                label="TopCrop+BodyWater",
            )
        else:
            task_top = _call_gpt_vision(client, top_b64, SYSTEM_PROMPT_TOP, "TopCrop")
        task_bottom = _call_gpt_vision(client, bottom_b64, SYSTEM_PROMPT_BOTTOM, "BottomCrop")
        task_wc = _call_gpt_vision(client, wc_b64, SYSTEM_PROMPT_WEIGHT_CONTROL, "WeightControlCrop")
        data_top, data_bottom, data_wc = await asyncio.gather(task_top, task_bottom, task_wc)
        data_top = data_top or {}
        data_bottom = data_bottom or {}
        data_wc = data_wc or {}

        # 3.4) 단백질/무기질이 0 또는 누락이면 표 부분 확대 크롭 fallback
        def _is_missing_or_suspicious(v) -> bool:
            if v is None:
                return True
            if isinstance(v, str) and not v.strip():
                return True
            # OCR에서 못 읽으면 0으로 뱉는 케이스가 많아서, 단백질/무기질에 한해 0은 의심값
            try:
                return float(v) == 0.0
            except Exception:
                return True

        # 모델이 새 키(protein/minerals) 또는 구 키(proteinKg/mineralsKg)로 반환할 수 있어 둘 다 체크
        if _is_missing_or_suspicious(data_top.get("protein")) or _is_missing_or_suspicious(data_top.get("proteinKg")) \
           or _is_missing_or_suspicious(data_top.get("minerals")) or _is_missing_or_suspicious(data_top.get("mineralsKg")):
            logger.info("[OCR] protein/minerals missing or suspicious. BodyComp micro fallback call...")
            data_bc = await _call_gpt_vision(client, bc_b64, SYSTEM_PROMPT_BODYCOMP_MICRO, "BodyComp Micro Fallback", vision_detail="high")
            if data_bc:
                data_top = {**data_top, **data_bc}

        # 3.4-2) 체중/골격근량/체지방량이 이상하면(누락/0) 골격근·지방 분석 미세 크롭 fallback
        mf_keys = ("weight", "weightKg", "skeletalMuscleMass", "skeletalMuscleMassKg", "bodyFatMass", "bodyFatMassKg")
        mf_suspicious = False
        for k in mf_keys:
            if k in ("weight", "skeletalMuscleMass", "bodyFatMass") and _is_missing_or_suspicious(data_top.get(k)):
                mf_suspicious = True
                break
            if k.endswith("Kg") and _is_missing_or_suspicious(data_top.get(k)):
                mf_suspicious = True
                break
        if mf_suspicious:
            logger.info("[OCR] weight/smm/bodyFatMass missing or suspicious. Muscle-Fat micro fallback call...")
            mf_prompt = """
너는 인바디(InBody) 결과지의 '골격근·지방 분석(Muscle-Fat Analysis)' 섹션만 추출하는 OCR 전문가야.
이 이미지는 해당 섹션만 잘라낸 이미지다.

다음 3개 값만 JSON으로 반환해:
- weight: 체중
- skeletalMuscleMass: 골격근량
- bodyFatMass: 체지방량

규칙:
- 신체변화(Body Composition History) 그래프 숫자/표준범위 숫자는 절대 쓰지 마.
- 단위 제거하고 숫자만.
- 안 보이면 null.
- JSON 형식 외에는 아무 말도 하지 마.
"""
            data_mf = await _call_gpt_vision(client, mf_b64, mf_prompt, "MuscleFat Micro Fallback", vision_detail="high")
            if data_mf:
                data_top = {**data_top, **data_mf}

        # 3.4-3) 체지방률이 이상하면 비만진단 미세 크롭 fallback
        if _is_missing_or_suspicious(data_bottom.get("bodyFatPercent")):
            logger.info("[OCR] bodyFatPercent missing or suspicious. Obesity micro fallback call...")
            ob_prompt = """
너는 인바디(InBody) 결과지의 '비만 진단(Obesity Analysis)' 섹션에서 체지방률만 추출하는 OCR 전문가야.
이 이미지는 해당 섹션만 잘라낸 이미지다.

다음 1개 값만 JSON으로 반환해:
- bodyFatPercent: 체지방률

규칙:
- 신체변화 그래프 숫자/표준범위 숫자는 절대 쓰지 마.
- 단위 제거하고 숫자만.
- 안 보이면 null.
- JSON 형식 외에는 아무 말도 하지 마.
"""
            data_ob = await _call_gpt_vision(client, ob_b64, ob_prompt, "Obesity Micro Fallback", vision_detail="high")
            if data_ob:
                data_bottom = {**data_bottom, **data_ob}

        # 3.5) 체중조절이 누락/전부 0이면 fallback(같은 크롭이지만 high detail로 1회 재시도)
        wc_keys = ("targetWeight", "weightControl", "fatControl", "muscleControl")
        missing_wc = []
        for k in wc_keys:
            v = data_wc.get(k, None)
            if v is None or (isinstance(v, str) and not v.strip()):
                missing_wc.append(k)

        # "전부 0"이면 거의 항상 못 읽은 거라 의심값으로 간주하고 fallback
        wc_all_zero = True
        for k in wc_keys:
            v = data_wc.get(k, None)
            try:
                if v is None:
                    wc_all_zero = False
                    break
                if float(v) != 0.0:
                    wc_all_zero = False
                    break
            except Exception:
                wc_all_zero = False
                break

        if missing_wc or wc_all_zero:
            logger.info(f"[OCR] WeightControl fallback. missing={missing_wc}, all_zero={wc_all_zero}")
            data_wc2 = await _call_gpt_vision(client, wc_b64, SYSTEM_PROMPT_WEIGHT_CONTROL, "WeightControl Fallback", vision_detail="high")
            if data_wc2:
                # fallback 결과는 null이 아닌 값만 덮어쓰기
                merged_wc = dict(data_wc)
                for k, v in (data_wc2 or {}).items():
                    if v is None:
                        continue
                    if isinstance(v, str) and not v.strip():
                        continue
                    merged_wc[k] = v
                data_wc = merged_wc
        
        # 4. 결과 병합 (top/bottom/wc)
        merged_data = {**data_top, **data_bottom, **data_wc}
        
        # 중복 필드(체중 등)가 있다면 좌측 값 우선
        if data_top.get("weight") is not None:
            merged_data["weight"] = data_top["weight"]
        elif data_top.get("weightKg") is not None:
            merged_data["weightKg"] = data_top["weightKg"]
            
        logger.info(f"[OCR] Merged Data: {merged_data}")
        
        return normalize_inbody_data(merged_data)

    except Exception as e:
        logger.error(f"[OCR] Critical Error: {str(e)}", exc_info=True)
        return {"error": str(e)}

async def _call_gpt_vision(client, b64_img, system_prompt, label, vision_detail: str | None = None):
    """GPT-4o Vision 호출 헬퍼"""
    try:
        detail = vision_detail or OCR_VISION_DETAIL
        logger.info(f"[OCR] Calling GPT ({label})...")
        response = await client.chat.completions.create(
            model=OCR_OPENAI_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": [
                    {"type": "text", "text": "이미지에서 데이터를 추출해줘."},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_img}", "detail": detail}}
                ]}
            ],
            max_tokens=600,
            temperature=0.0,
            response_format={"type": "json_object"}
        )
        content = response.choices[0].message.content
        return json.loads(content)
    except Exception as e:
        logger.error(f"[OCR] GPT Error ({label}): {e}")
        return {}

async def _call_gpt_vision_multi(
    client,
    images: list[dict],
    system_prompt: str,
    label: str,
    vision_detail: str | None = None,
):
    """GPT-4o Vision 호출 (여러 이미지)"""
    try:
        detail = vision_detail or OCR_VISION_DETAIL
        logger.info(f"[OCR] Calling GPT ({label}) [multi-images={len(images)}]...")

        user_content = [{"type": "text", "text": "아래 이미지들에서 필요한 값을 추출해줘. 이미지 힌트를 엄격히 따라."}]
        for img in images:
            hint = (img.get("hint") or "").strip()
            if hint:
                user_content.append({"type": "text", "text": hint})
            mime = (img.get("mime") or "image/jpeg").strip()
            b64 = img.get("b64") or ""
            user_content.append(
                {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}", "detail": detail}}
            )

        response = await client.chat.completions.create(
            model=OCR_OPENAI_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content},
            ],
            max_tokens=600,
            temperature=0.0,
            response_format={"type": "json_object"},
        )
        content = response.choices[0].message.content
        return json.loads(content)
    except Exception as e:
        logger.error(f"[OCR] GPT Error ({label}): {e}")
        return {}

def _image_to_base64(img: Image.Image) -> str:
    """PIL 이미지를 Base64 문자열로 변환"""
    buffered = BytesIO()
    img.save(buffered, format="JPEG", quality=OCR_JPEG_QUALITY, optimize=True)
    return base64.b64encode(buffered.getvalue()).decode("utf-8")

def _image_to_base64_png(img: Image.Image) -> str:
    """PIL 이미지를 PNG(Base64)로 변환 (무손실: 체수분 숫자 획 보존용)"""
    buffered = BytesIO()
    img.save(buffered, format="PNG", optimize=True)
    return base64.b64encode(buffered.getvalue()).decode("utf-8")

def _downscale_if_needed(img: Image.Image, max_width: int) -> Image.Image:
    """이미지가 너무 큰 경우에만 너비 기준으로 다운스케일"""
    if not max_width or max_width <= 0:
        return img
    img = img.convert("RGB")
    w, h = img.size
    if w <= 0 or h <= 0:
        return img
    if w > max_width:
        new_h = int(h * (max_width / w))
        return img.resize((max_width, new_h), Image.LANCZOS)
    return img

def _upscale_for_readability(img: Image.Image, target_width: int) -> Image.Image:
    """
    작은 글씨 영역을 읽기 쉽게 하기 위해, 크롭된 이미지를 적당히 업스케일 + 대비 강화.
    업스케일은 토큰을 늘리지만, 크롭이 작기 때문에 전체 처리 시간은 크게 늘지 않으면서 정확도를 크게 올립니다.
    """
    img = img.convert("RGB")
    w, h = img.size
    if w <= 0 or h <= 0:
        return img
    if w < target_width:
        new_h = int(h * (target_width / w))
        img = img.resize((target_width, new_h), Image.LANCZOS)
    # 크롭에는 조금 더 강한 대비를 적용
    try:
        img = ImageEnhance.Contrast(img).enhance(max(OCR_CONTRAST, 1.25))
    except Exception:
        pass
    return img

def _upscale_for_readability_soft(img: Image.Image, target_width: int) -> Image.Image:
    """
    체수분(bodyWater) 전용: 과한 대비/샤프닝으로 숫자 모양이 변형되는 것을 피하기 위해
    업스케일만 수행하고 추가 대비 강제(max(...,1.25))는 적용하지 않습니다.
    """
    img = img.convert("RGB")
    w, h = img.size
    if w <= 0 or h <= 0:
        return img
    if w < target_width:
        new_h = int(h * (target_width / w))
        img = img.resize((target_width, new_h), Image.LANCZOS)
    return img

def _load_image_minimal(image_bytes: bytes) -> Image.Image:
    """EXIF 보정 + RGB 변환만 수행 (대비/샤프닝 등은 적용하지 않음)"""
    img = Image.open(BytesIO(image_bytes))
    return ImageOps.exif_transpose(img).convert("RGB")

def _crop_body_water_tight(img: Image.Image) -> Image.Image:
    """
    체성분분석 표의 '체수분 측정치' 숫자만 최대한 타이트하게 포함하도록 크롭.
    - 같은 셀 안에 '(표준범위 ...~...)'가 붙어 있어, 세로폭을 짧게 잡아 범위값이 같이 들어오지 않게 합니다.
    """
    w, h = img.size

    # Body Composition Analysis 영역(좌측 상단 표)을 기준으로 상대 좌표 산정 (InBody 370S 포맷)
    # 기존 bc_img(0.10~0.42h)는 너무 커서 y 비율이 어긋나기 쉬워, 체성분표 높이로 축소합니다.
    bc_x1 = 0
    bc_y1 = int(h * 0.10)
    bc_x2 = int(w * 0.62)
    bc_y2 = int(h * 0.27)
    bc_w = max(1, bc_x2 - bc_x1)
    bc_h = max(1, bc_y2 - bc_y1)

    # 빨간 박스 위치(체수분 측정치 43.9) 기준으로:
    # - x축: 라벨(체수분) 오른쪽의 '측정치 숫자' 박스만 포함
    # - y축: 첫 행의 '측정치 숫자'만 포함하고, 아래쪽 '(표준범위 ...~...)'는 제외
    x1 = int(bc_x1 + bc_w * 0.30)
    x2 = int(bc_x1 + bc_w * 0.52)
    y1 = int(bc_y1 + bc_h * 0.20)
    y2 = int(bc_y1 + bc_h * 0.40)

    # clamp
    x1 = max(0, min(x1, w - 1))
    y1 = max(0, min(y1, h - 1))
    x2 = max(x1 + 1, min(x2, w))
    y2 = max(y1 + 1, min(y2, h))

    return img.crop((x1, y1, x2, y2))

def _load_and_prepare_image(image_bytes: bytes) -> Image.Image:
    """
    - EXIF 회전 보정
    - RGB 변환
    - 너무 큰 원본은 리사이즈(속도 개선)
    - 약한 대비 보정(작은 글씨 인식률 개선)
    """
    img = Image.open(BytesIO(image_bytes))
    img = ImageOps.exif_transpose(img).convert("RGB")

    # 원본 전체 리사이즈는 기본적으로 하지 않음(정확도 우선).
    # 필요 시 OCR_MAX_WIDTH(>0)로만 제한.
    if OCR_MAX_WIDTH and OCR_MAX_WIDTH > 0:
        w, h = img.size
        if w > OCR_MAX_WIDTH:
            new_h = int(h * (OCR_MAX_WIDTH / w))
            img = img.resize((OCR_MAX_WIDTH, new_h), Image.LANCZOS)

    if OCR_CONTRAST and OCR_CONTRAST > 1.0:
        img = ImageEnhance.Contrast(img).enhance(OCR_CONTRAST)

    if OCR_SHARPNESS and OCR_SHARPNESS > 1.0:
        try:
            img = ImageEnhance.Sharpness(img).enhance(OCR_SHARPNESS)
        except Exception:
            pass

    return img

def normalize_inbody_data(data: Dict[str, Any]) -> Dict[str, Any]:
    """OCR 결과 데이터 정규화"""
    normalized = {}

    # Canonical keys (단위 없는 키)
    fields = [
        "height", "weight", "skeletalMuscleMass", "bodyFatMass",
        "bodyFatPercent", "bodyWater", "protein", "minerals",
        "targetWeight", "weightControl", "fatControl", "muscleControl",
    ]

    # Backward-compat aliases (예전 키 지원)
    aliases = {
        "height": ["heightCm"],
        "weight": ["weightKg"],
        "skeletalMuscleMass": ["skeletalMuscleMassKg"],
        "bodyFatMass": ["bodyFatMassKg"],
        "bodyWater": ["bodyWaterL"],
        "protein": ["proteinKg"],
        "minerals": ["mineralsKg"],
    }

    def _pick_value(key: str):
        if key in data and data.get(key) is not None:
            return data.get(key)
        for a in aliases.get(key, []):
            if a in data and data.get(a) is not None:
                return data.get(a)
        return None

    for field in fields:
        val = _pick_value(field)
        if val is None:
            normalized[field] = None
            continue

        if isinstance(val, (int, float)):
            normalized[field] = float(val)
            continue

        if isinstance(val, str):
            match = re.search(r"([+-]?\d*\.?\d+)", val)
            if match:
                try:
                    normalized[field] = float(match.group(1))
                except ValueError:
                    normalized[field] = None
            else:
                normalized[field] = None
            continue

        normalized[field] = None

    # 검사일시는 문자열 그대로
    normalized["measuredDate"] = data.get("measuredDate")

    return normalized
