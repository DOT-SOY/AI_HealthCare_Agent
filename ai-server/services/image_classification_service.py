"""
이미지 분류 서비스
CLIP 모델의 텍스트-이미지 매칭을 사용하여 인바디인지 음식인지 간단하게 판단합니다.
"""
from typing import Dict, Any
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
        print(f"[이미지 분류] CLIP 모델 로딩 중... (device: {_device})")
        model_name = "openai/clip-vit-base-patch32"
        _clip_model = CLIPModel.from_pretrained(model_name).to(_device)
        _clip_processor = CLIPProcessor.from_pretrained(model_name)
        _clip_model.eval()
        print("[이미지 분류] CLIP 모델 로딩 완료")
    return _clip_model, _clip_processor


class ImageClassificationService:
    """이미지 분류 서비스 - 인바디/음식 사진 분류 (간단한 텍스트-이미지 매칭)"""
    
    def classify_image(self, image_bytes: bytes) -> Dict[str, Any]:
        """
        입력 이미지를 분류하여 인바디인지 음식인지 판단합니다.
        CLIP 모델의 텍스트-이미지 유사도를 사용합니다.
        
        Args:
            image_bytes: 입력 이미지 바이트 데이터
            
        Returns:
            {
                "type": "inbody" or "food",
                "confidence": float,  # 유사도 점수 (0~1)
            }
        """
        try:
            model, processor = _get_clip_model()
            
            # 이미지 전처리
            image = Image.open(BytesIO(image_bytes))
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # 인바디 관련 텍스트 프롬프트들 (더 구체적으로)
            inbody_texts = [
                "인바디 측정기 화면",
                "체성분 분석기 디스플레이",
                "인바디 측정 결과 화면",
                "체지방률 골격근량 측정 화면",
                "body composition analyzer screen",
                "inbody machine display",
                "body fat percentage measurement device",
                "bioelectrical impedance analysis device"
            ]
            
            # 음식 관련 텍스트 프롬프트들 (더 구체적으로)
            food_texts = [
                "음식 사진",
                "식사 사진",
                "요리 사진",
                "음식 이미지",
                "food photo",
                "meal photo",
                "dish photo",
                "food image",
                "cooked food",
                "delicious food"
            ]
            
            # 이미지와 텍스트를 함께 처리
            image_input = processor(images=image, return_tensors="pt").to(_device)
            inbody_text_input = processor(text=inbody_texts, return_tensors="pt", padding=True, truncation=True).to(_device)
            food_text_input = processor(text=food_texts, return_tensors="pt", padding=True, truncation=True).to(_device)
            
            with torch.no_grad():
                # 이미지 임베딩
                image_features = model.get_image_features(**image_input)
                image_features = image_features / image_features.norm(dim=-1, keepdim=True)
                
                # 텍스트 임베딩
                inbody_text_features = model.get_text_features(**inbody_text_input)
                inbody_text_features = inbody_text_features / inbody_text_features.norm(dim=-1, keepdim=True)
                
                food_text_features = model.get_text_features(**food_text_input)
                food_text_features = food_text_features / food_text_features.norm(dim=-1, keepdim=True)
                
                # 유사도 계산 (cosine similarity)
                inbody_similarities = (image_features @ inbody_text_features.T).cpu().numpy()[0]
                food_similarities = (image_features @ food_text_features.T).cpu().numpy()[0]
                
                # 최대 유사도
                max_inbody_sim = float(max(inbody_similarities))
                max_food_sim = float(max(food_similarities))
                
                # 평균 유사도
                avg_inbody_sim = float(inbody_similarities.mean())
                avg_food_sim = float(food_similarities.mean())
                
                # 최종 점수 (최대값과 평균값의 가중 평균)
                inbody_score = (max_inbody_sim * 0.6) + (avg_inbody_sim * 0.4)
                food_score = (max_food_sim * 0.6) + (avg_food_sim * 0.4)
                
                # 점수 차이 계산
                score_diff = abs(inbody_score - food_score)
                
                print(f"[이미지 분류] 인바디 점수: {inbody_score:.3f}, 음식 점수: {food_score:.3f}, 점수 차이: {score_diff:.3f}")
                
                # 분류 로직:
                # 1. 인바디 점수가 음식 점수보다 높으면 기본적으로 인바디로 분류
                # 2. 단, 둘 다 매우 낮으면 (0.2 미만) 불확실하므로 음식으로 간주 (기본값)
                # 3. 인바디 점수가 충분히 높으면 (0.3 이상) 무조건 인바디로 분류
                
                LOW_CONFIDENCE_THRESHOLD = 0.2  # 둘 다 낮으면 불확실
                HIGH_INBODY_THRESHOLD = 0.3  # 인바디 점수가 높으면 확실
                
                if inbody_score > food_score:
                    # 인바디 점수가 더 높은 경우
                    if inbody_score >= HIGH_INBODY_THRESHOLD:
                        # 인바디 점수가 충분히 높으면 무조건 인바디
                        image_type = "inbody"
                        confidence = inbody_score
                        print(f"[이미지 분류] 인바디로 분류 (높은 인바디 점수: {inbody_score:.3f} >= {HIGH_INBODY_THRESHOLD})")
                    elif inbody_score < LOW_CONFIDENCE_THRESHOLD and food_score < LOW_CONFIDENCE_THRESHOLD:
                        # 둘 다 너무 낮으면 불확실하므로 음식으로 간주
                        image_type = "food"
                        confidence = food_score
                        print(f"[이미지 분류] 음식으로 분류 (둘 다 낮음: 인바디={inbody_score:.3f}, 음식={food_score:.3f} < {LOW_CONFIDENCE_THRESHOLD})")
                    else:
                        # 인바디 점수가 음식보다 높고, 어느 정도 신뢰도가 있으면 인바디로 분류
                        image_type = "inbody"
                        confidence = inbody_score
                        print(f"[이미지 분류] 인바디로 분류 (인바디 점수가 더 높음: {inbody_score:.3f} > {food_score:.3f})")
                else:
                    # 음식 점수가 더 높은 경우
                    image_type = "food"
                    confidence = food_score
                    print(f"[이미지 분류] 음식으로 분류 (음식 점수가 더 높음: {food_score:.3f} > {inbody_score:.3f})")
                
                print(f"[이미지 분류] 최종 타입: {image_type}, 신뢰도: {confidence:.3f}")
                
                return {
                    "type": image_type,
                    "confidence": confidence,
                    "nearest_point_id": None,
                    "metadata": {
                        "inbody_score": inbody_score,
                        "food_score": food_score
                    }
                }
                
        except Exception as e:
            print(f"이미지 분류 실패: {e}")
            import traceback
            traceback.print_exc()
            return {
                "type": "food",  # 에러 시 음식으로 간주
                "confidence": 0.0,
                "nearest_point_id": None,
                "error": str(e)
            }
    

# 싱글톤 인스턴스
_image_classification_service = None

def get_image_classification_service() -> ImageClassificationService:
    """이미지 분류 서비스 싱글톤 인스턴스 반환"""
    global _image_classification_service
    if _image_classification_service is None:
        _image_classification_service = ImageClassificationService()
    return _image_classification_service

