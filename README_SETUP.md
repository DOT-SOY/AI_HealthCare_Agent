# GrowLog 프론트엔드 및 파이썬 AI 서버 설정 가이드

## 프로젝트 구조

```
AI_HealthCare_Agent/
├── backend/          # Spring Boot 백엔드
├── frontend/         # React 프론트엔드
├── ai-server/        # FastAPI 파이썬 AI 서버
├── rag_data.json     # RAG 초기 데이터
└── setup_rag.py      # RAG 초기화 스크립트
```

## 1. 프론트엔드 설정

### 1.1 의존성 설치

```bash
cd frontend
npm install
```

### 1.2 개발 서버 실행

```bash
npm run dev
```

- 기본 주소: `http://localhost:5173`
- 백엔드 프록시: `/api` → `http://localhost:8080`
- WebSocket 프록시: `/ws` → `ws://localhost:8080`

## 2. 파이썬 AI 서버 설정

### 2.1 Python 버전 확인

```bash
python --version  # Python 3.10 이상 권장
```

### 2.2 가상환경 생성 및 활성화

**Windows PowerShell:**
```bash
cd ai-server
python -m venv venv
venv\Scripts\activate
```

**Linux/Mac:**
```bash
cd ai-server
python3 -m venv venv
source venv/bin/activate
```

### 2.3 의존성 설치

```bash
pip install -r requirements.txt
```

### 2.4 환경 변수 설정

`ai-server` 폴더에 `.env` 파일 생성:

```bash
cd ai-server
# .env.example을 복사하여 .env 파일 생성
copy .env.example .env  # Windows
# cp .env.example .env  # Linux/Mac
```

`.env` 파일을 열어서 `OPENAI_API_KEY`에 실제 API 키를 입력하세요:

```env
OPENAI_API_KEY=sk-여기에_실제_키
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=exercise_knowledge
OPENAI_MODEL=gpt-4o-mini
EMBEDDING_MODEL=text-embedding-3-small
```

### 2.5 RAG 데이터 초기화

**Qdrant가 실행 중이어야 합니다** (다음 섹션 참조)

```bash
# 프로젝트 루트에서 실행
python setup_rag.py
```

### 2.6 AI 서버 실행

```bash
cd ai-server
uvicorn main:app --reload --port 8000
```

- API 문서: `http://localhost:8000/docs` (Swagger UI)
- 헬스 체크: `http://localhost:8000/health`

## 3. Qdrant 설정 (선택사항)

Qdrant는 RAG 기능을 위해 사용됩니다. Qdrant 없이도 AI 서버는 동작하지만, RAG 검색은 사용할 수 없습니다.

### 3.1 Docker로 Qdrant 실행

```bash
docker run -d -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage --name qdrant qdrant/qdrant
```

### 3.2 실행 확인

- 대시보드: `http://localhost:6333/dashboard`
- API: `http://localhost:6333`

## 4. 백엔드 설정

백엔드는 이미 설정되어 있다고 가정합니다.

- 포트: `8080`
- WebSocket: `ws://localhost:8080/ws`

## 5. 전체 실행 순서

1. **Qdrant 실행** (선택사항)
   ```bash
   docker run -d -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage --name qdrant qdrant/qdrant
   ```

2. **RAG 데이터 초기화** (Qdrant 실행 후)
   ```bash
   python setup_rag.py
   ```

3. **파이썬 AI 서버 실행**
   ```bash
   cd ai-server
   venv\Scripts\activate  # Windows
   # source venv/bin/activate  # Linux/Mac
   uvicorn main:app --reload --port 8000
   ```

4. **백엔드 실행**
   ```bash
   cd backend
   .\gradlew bootRun  # Windows
   # ./gradlew bootRun  # Linux/Mac
   ```

5. **프론트엔드 실행**
   ```bash
   cd frontend
   npm run dev
   ```

## 6. 테스트

### 프론트엔드
- 브라우저에서 `http://localhost:5173` 접속
- 오늘의 루틴 페이지 확인
- AI 채팅 오버레이 테스트

### 파이썬 AI 서버
- `http://localhost:8000/docs`에서 API 문서 확인
- `/health` 엔드포인트 테스트
- `/chat` 엔드포인트 테스트

## 7. 문제 해결

### Qdrant 연결 실패
- Qdrant가 실행 중인지 확인: `docker ps`
- 포트가 올바른지 확인: `http://localhost:6333`
- AI 서버는 Qdrant 없이도 동작합니다 (RAG 없이)

### OpenAI API 오류
- `.env` 파일의 `OPENAI_API_KEY`가 올바른지 확인
- API 키가 활성화되어 있는지 확인

### 프론트엔드에서 백엔드 연결 실패
- 백엔드가 `http://localhost:8080`에서 실행 중인지 확인
- CORS 설정 확인

### WebSocket 연결 실패
- 백엔드 WebSocket 설정 확인
- 브라우저 콘솔에서 오류 메시지 확인

