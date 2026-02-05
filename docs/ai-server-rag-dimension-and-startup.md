# ai-server 로컬 RAG: 384차원 통일 및 동작 보장

## 선택 정리

- **채택**: Qdrant 컬렉션을 **384차원(SentenceTransformer)** 기준으로 통일.
- **이유**: 이미 `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`로 임베딩을 생성하고 있으며, 런타임 API 비용 없이 로컬에서 동작. OpenAI 1536으로 통일하면 매 요청당 임베딩 비용·지연이 발생.

## 해야 할 일 (운영)

1. **Qdrant 컬렉션 384차원 재생성 및 재임베딩**
   - 기존 1536차원 컬렉션이 있으면 `expected dim: 1536, got 384` 오류로 RAG 검색이 실패함.
   - 프로젝트 루트에서:
     ```bash
     RECREATE_COLLECTION=true python setup_rag.py
     ```
   - `setup_rag.py`는 `ST_MODEL_NAME`(기본값: paraphrase-multilingual-MiniLM-L12-v2)로 임베딩을 생성하고, 동일 차원(384)으로 컬렉션을 생성·업로드함.

2. **ai-server 재시작**
   - 앱 startup 시 임베딩 모델을 1회 로딩하므로, 서버 재시작 후 첫 요청에서 8초 지연이 사라짐.

## 코드 측 변경 요약

| 항목 | 내용 |
|------|------|
| **차원 상수** | `embedding_service.EMBEDDING_DIM = 384`, `load_embedding_model()` 추가 |
| **Startup 로딩** | `main.py` lifespan에서 `load_embedding_model()` 1회 호출 (요청 중 로딩 제거) |
| **RAG 검색 반환** | `search_commerce_rag`가 `{ results, rag_hit, retrieved_chunks, error? }` 반환, 실패 시에도 명시 |
| **차원 검증** | 검색 전 Qdrant 컬렉션 벡터 차원 확인, 384가 아니면 `dimension_mismatch` 반환 및 로그 |
| **실패 로그** | `rag_hit=false`, `retrieved_chunks=0`, `error=...` 로그로 RAG 실패를 명시 |
| **setup_rag** | 384 기대 차원 주석·경고, RECREATE로 384 재생성 안내 |

## RAG 실패 시 동작

- **이전**: 예외를 삼키고 `[]` 반환 → LLM만으로 진행 (조용히 실패).
- **이후**: `rag_hit=false`, `retrieved_chunks=0`, `error`(예: `dimension_mismatch`, `embedding_failed`) 반환 및 로그 → 호출부에서 fallback 문구로 LLM 진행하되, 로그로 원인 파악 가능.
