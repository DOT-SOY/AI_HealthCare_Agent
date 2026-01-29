# AI Meal Service (Python)

Gemini 3 Pro 기반 식단 관리 AI 서버

## 프로젝트 구조

```
python/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 앱 진입점
│   ├── config.py            # 환경변수 설정
│   ├── models/
│   │   ├── request.py       # 요청 DTO
│   │   └── response.py      # 응답 DTO
│   ├── services/
│   │   ├── gemini_service.py    # Gemini 3 Pro 통합
│   │   ├── vision_service.py    # Vision 분석
│   │   └── rag_service.py        # Qdrant RAG
│   ├── routers/
│   │   └── meal.py          # API 엔드포인트
│   └── utils/
│       └── rag_setup.py      # RAG 데이터 설정
├── venv/                     # 가상환경
├── .env                      # 환경변수
├── requirements.txt
└── setup_rag.py             # RAG 데이터 업로드 스크립트
```

## 설치 및 실행

### 1. Qdrant 실행 (Docker)

```powershell
docker run -d -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage --name qdrant qdrant/qdrant
```

### 2. 환경 설정

`.env.example`을 복사하여 `.env` 파일 생성:

```powershell
copy .env.example .env
```

`.env` 파일에 Gemini API 키 등 설정:

```
GEMINI_API_KEY=your_api_key_here
```

### 3. 가상환경 생성 및 활성화

```powershell
cd python
python -m venv venv
venv\Scripts\activate
```

### 4. 패키지 설치

```powershell
pip install -r requirements.txt
```

### 5. RAG 데이터 업로드 (선택)

프로젝트 루트에서 실행:

```powershell
python setup_rag.py
```

### 6. 서버 실행

```powershell
cd python
venv\Scripts\activate
uvicorn app.main:app --reload --port 8000
```

## API 엔드포인트

- `POST /api/meal/analyze`: 통합 AI 요청 처리
  - `requestType`: ANALYZE_IMAGE, ADVICE, REPLAN, GENERATE

## Java 연동

Java 백엔드의 `AiMealClient`가 `http://localhost:8000/api/meal/analyze`로 요청을 보냅니다.

