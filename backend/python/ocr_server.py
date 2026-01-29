from fastapi import FastAPI, UploadFile, File
from paddleocr import PaddleOCR
import easyocr
import uvicorn
import shutil
import os

app = FastAPI()

# --- 1. PaddleOCR 초기화 (한국어) ---
paddle_model = PaddleOCR(lang='korean', use_angle_cls=True)

# --- 2. EasyOCR 초기화 (한국어+영어) ---
easy_reader = easyocr.Reader(['ko', 'en'])

@app.post("/ocr/paddle")
async def run_paddle(file: UploadFile = File(...)):
    # 파일 임시 저장
    temp_file = f"temp_{file.filename}"
    with open(temp_file, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # 분석
    result = paddle_model.ocr(temp_file, cls=True)

    # 결과 정리
    texts = []
    for idx in range(len(result)):
        res = result[idx]
        for line in res:
            texts.append(line[1][0]) # 텍스트만 추출

    os.remove(temp_file) # 임시파일 삭제
    return {"engine": "PaddleOCR", "text": "\n".join(texts)}

@app.post("/ocr/easy")
async def run_easy(file: UploadFile = File(...)):
    # 파일 임시 저장
    temp_file = f"temp_easy_{file.filename}"
    with open(temp_file, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # 분석
    result = easy_reader.readtext(temp_file, detail=0) # detail=0이면 텍스트만 리스트로 줌

    os.remove(temp_file)
    return {"engine": "EasyOCR", "text": "\n".join(result)}

if __name__ == "__main__":
    print("🚀 OCR Python Server Running on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)