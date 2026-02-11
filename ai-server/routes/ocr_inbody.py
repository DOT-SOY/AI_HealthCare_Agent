from fastapi import APIRouter, UploadFile, File, HTTPException
from services.ocr_inbody_service import analyze_inbody_image
from pydantic import BaseModel
from typing import Optional, Dict, Any

router = APIRouter(prefix="/inbody", tags=["ocr"])

class InbodyAnalyzeResponse(BaseModel):
    intent: str = "INBODY_ANALYSIS"
    message: str
    data: Optional[Dict[str, Any]] = None

@router.post("/analyze", response_model=InbodyAnalyzeResponse)
async def analyze_inbody(file: UploadFile = File(...)):
    """
    인바디 이미지 업로드 -> GPT-4o mini Vision OCR -> 구조화된 데이터 반환
    """
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다.")
    
    try:
        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="파일 내용이 비어있습니다.")
            
        result = await analyze_inbody_image(content)
        
        if "error" in result:
            return InbodyAnalyzeResponse(
                message=f"분석 실패: {result['error']}",
                data=None
            )
            
        return InbodyAnalyzeResponse(
            message="인바디 분석이 완료되었습니다.",
            data=result
        )
        
    except Exception as e:
        print(f"[OCR Route] Error: {e}")
        return InbodyAnalyzeResponse(
            message="서버 내부 오류가 발생했습니다.",
            data=None
        )




