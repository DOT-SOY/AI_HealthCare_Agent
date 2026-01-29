"""
RAG 데이터를 Qdrant에 업로드하는 스크립트
"""
import json
import os
from dotenv import load_dotenv
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from openai import OpenAI

# 환경 변수 로드 (ai-server 폴더의 .env 파일)
import pathlib
env_path = pathlib.Path(__file__).parent / 'ai-server' / '.env'
load_dotenv(dotenv_path=env_path)

# 클라이언트 초기화
openai_client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")

qdrant_url = os.getenv("QDRANT_URL", "http://localhost:6333")
qdrant_client = QdrantClient(url=qdrant_url)
QDRANT_COLLECTION = os.getenv("QDRANT_COLLECTION", "exercise_knowledge")


def load_knowledge() -> list:
    """rag_data.json 파일 로드"""
    try:
        with open("rag_data.json", "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print("rag_data.json 파일을 찾을 수 없습니다.")
        return []
    except json.JSONDecodeError as e:
        print(f"JSON 파싱 오류: {e}")
        return []


def get_embedding(text: str) -> list:
    """OpenAI 임베딩 생성"""
    try:
        response = openai_client.embeddings.create(
            model=EMBEDDING_MODEL,
            input=text
        )
        return response.data[0].embedding
    except Exception as e:
        print(f"임베딩 생성 실패: {e}")
        return None


def ensure_collection(client: QdrantClient, vector_size: int = 1536):
    """Qdrant 컬렉션 생성/재생성"""
    try:
        # 기존 컬렉션 삭제 (있는 경우)
        try:
            client.delete_collection(QDRANT_COLLECTION)
            print(f"기존 컬렉션 '{QDRANT_COLLECTION}' 삭제됨")
        except Exception:
            pass
        
        # 새 컬렉션 생성
        client.create_collection(
            collection_name=QDRANT_COLLECTION,
            vectors_config=VectorParams(
                size=vector_size,
                distance=Distance.COSINE
            )
        )
        print(f"컬렉션 '{QDRANT_COLLECTION}' 생성됨")
    except Exception as e:
        print(f"컬렉션 생성 실패: {e}")
        raise


def main():
    """메인 실행 함수"""
    print("RAG 데이터 초기화 시작...")
    
    # 지식 데이터 로드
    knowledge_data = load_knowledge()
    if not knowledge_data:
        print("로드할 데이터가 없습니다.")
        return
    
    print(f"총 {len(knowledge_data)}개의 지식 항목 로드됨")
    
    # 첫 번째 항목으로 벡터 크기 확인
    sample_text = f"{knowledge_data[0].get('title', '')} {knowledge_data[0].get('content', '')}"
    sample_embedding = get_embedding(sample_text)
    if not sample_embedding:
        print("임베딩 생성 실패로 인해 중단됩니다.")
        return
    
    vector_size = len(sample_embedding)
    print(f"벡터 크기: {vector_size}")
    
    # 컬렉션 생성/재생성
    ensure_collection(qdrant_client, vector_size)
    
    # 각 항목을 벡터화하여 업로드
    points = []
    for idx, item in enumerate(knowledge_data):
        # 텍스트 결합 (제목 + 내용)
        text = f"{item.get('title', '')} {item.get('content', '')}"
        
        # 임베딩 생성
        embedding = get_embedding(text)
        if not embedding:
            print(f"항목 {idx + 1} 임베딩 생성 실패, 건너뜀")
            continue
        
        # PointStruct 생성
        point = PointStruct(
            id=item.get("id", idx + 1),
            vector=embedding,
            payload={
                "category": item.get("category", ""),
                "title": item.get("title", ""),
                "content": item.get("content", ""),
                "body_part": item.get("body_part", ""),
                "exercise_name": item.get("exercise_name", ""),
                "tags": item.get("tags", [])
            }
        )
        points.append(point)
        
        print(f"항목 {idx + 1}/{len(knowledge_data)} 처리 완료: {item.get('title', '')}")
    
    # Qdrant에 업로드
    if points:
        try:
            qdrant_client.upsert(
                collection_name=QDRANT_COLLECTION,
                points=points
            )
            print(f"\n총 {len(points)}개의 항목이 Qdrant에 업로드되었습니다.")
        except Exception as e:
            print(f"업로드 실패: {e}")
    else:
        print("업로드할 항목이 없습니다.")
    
    print("\nRAG 데이터 초기화 완료!")


if __name__ == "__main__":
    main()

