"""
이미지 임베딩 서비스
CLIP 모델을 사용하여 이미지를 직접 임베딩 벡터로 변환합니다.
"""
from typing import List, Optional
from io import BytesIO
from PIL import Image
import torch
from transformers import CLIPProcessor, CLIPModel

# CLIP 모델 초기화 (싱글톤)
_clip_model = None
_clip_processor = None
_device = "cuda" if torch.cuda.is_available() else "cpu"

def _get_clip_model():
    """CLIP 모델 싱글톤 인스턴스 반환"""
    global _clip_model, _clip_processor
    if _clip_model is None:
        print(f"[이미지 임베딩] CLIP 모델 로딩 중... (device: {_device})")
        model_name = "openai/clip-vit-base-patch32"  # 경량 모델
        _clip_model = CLIPModel.from_pretrained(model_name).to(_device)
        _clip_processor = CLIPProcessor.from_pretrained(model_name)
        _clip_model.eval()  # 평가 모드
        print("[이미지 임베딩] CLIP 모델 로딩 완료")
    return _clip_model, _clip_processor


def get_image_embedding(image_bytes: bytes) -> Optional[List[float]]:
    """
    이미지 바이트를 받아 임베딩 벡터로 직접 변환합니다.
    
    Args:
        image_bytes: 이미지 바이트 데이터
        
    Returns:
        이미지 임베딩 벡터 (512차원) 또는 None
    """
    try:
        # CLIP 모델과 프로세서 가져오기
        model, processor = _get_clip_model()
        
        # 이미지 바이트를 PIL Image로 변환
        image = Image.open(BytesIO(image_bytes))
        
        # RGB로 변환 (RGBA나 다른 형식 처리)
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        # 이미지 전처리
        inputs = processor(images=image, return_tensors="pt").to(_device)
        
        # 이미지 임베딩 생성
        with torch.no_grad():
            image_features = model.get_image_features(**inputs)
            # 정규화
            image_features = image_features / image_features.norm(dim=-1, keepdim=True)
            # CPU로 이동하고 numpy로 변환
            embedding = image_features.cpu().numpy()[0].tolist()
        
        print(f"[이미지 임베딩] 임베딩 생성 완료 (차원: {len(embedding)})")
        return embedding
        
    except Exception as e:
        print(f"이미지 임베딩 생성 실패: {e}")
        import traceback
        traceback.print_exc()
        return None

