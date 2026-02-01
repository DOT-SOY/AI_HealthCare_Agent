"""
RAG 데이터를 Qdrant에 업로드하는 스크립트
"""
import json
import os
import uuid
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
QDRANT_COLLECTION_COMMERCE = os.getenv("QDRANT_COLLECTION_COMMERCE", "commerce_knowledge")
QDRANT_COLLECTION_EXERCISE = os.getenv("QDRANT_COLLECTION_EXERCISE", "exercise_knowledge")


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


def ensure_collection(client: QdrantClient, collection_name: str, vector_size: int = 1536):
    """Qdrant 컬렉션 생성/재생성"""
    try:
        # 기존 컬렉션 삭제 (있는 경우)
        try:
            client.delete_collection(collection_name)
            print(f"기존 컬렉션 '{collection_name}' 삭제됨")
        except Exception:
            pass
        
        # 새 컬렉션 생성
        client.create_collection(
            collection_name=collection_name,
            vectors_config=VectorParams(
                size=vector_size,
                distance=Distance.COSINE
            )
        )
        print(f"컬렉션 '{collection_name}' 생성됨")
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
    
    # domain별로 데이터 분리
    commerce_points = []
    exercise_points = []
    
    for idx, item in enumerate(knowledge_data):
        # 텍스트 결합 (제목 + 내용)
        text = f"{item.get('title', '')} {item.get('content', '')}"
        
        # 임베딩 생성
        embedding = get_embedding(text)
        if not embedding:
            print(f"항목 {idx + 1} 임베딩 생성 실패, 건너뜀")
            continue
        
        # payload 생성
        payload = {
            "category": item.get("category", ""),
            "title": item.get("title", ""),
            "content": item.get("content", ""),
            "body_part": item.get("body_part", ""),
            "exercise_name": item.get("exercise_name", ""),
            "tags": item.get("tags", [])
        }
        
        # 새로운 필드 추가
        if "doc_id" in item:
            payload["doc_id"] = item.get("doc_id")
        if "chunk_id" in item:
            payload["chunk_id"] = item.get("chunk_id")
        if "doc_type" in item:
            payload["doc_type"] = item.get("doc_type")
        if "goal" in item:
            payload["goal"] = item.get("goal")
        if "product_category" in item:
            payload["product_category"] = item.get("product_category")
        if "domain" in item:
            payload["domain"] = item.get("domain")
        if "version" in item:
            payload["version"] = item.get("version")
        if "section" in item:
            payload["section"] = item.get("section")
        
        # PointStruct 생성 (UUID 사용)
        point = PointStruct(
            id=str(uuid.uuid4()),
            vector=embedding,
            payload=payload
        )
        
        # domain에 따라 분리
        domain = item.get("domain", "")
        if domain == "commerce":
            commerce_points.append(point)
        else:
            exercise_points.append(point)
        
        print(f"항목 {idx + 1}/{len(knowledge_data)} 처리 완료: {item.get('title', '')}")
    
    # Commerce 컬렉션 처리
    if commerce_points:
        print(f"\n[Commerce 컬렉션] {len(commerce_points)}개 항목 처리 중...")
        ensure_collection(qdrant_client, QDRANT_COLLECTION_COMMERCE, vector_size)
        try:
            qdrant_client.upsert(
                collection_name=QDRANT_COLLECTION_COMMERCE,
                points=commerce_points
            )
            print(f"Commerce 컬렉션에 {len(commerce_points)}개 항목 업로드 완료")
        except Exception as e:
            print(f"Commerce 컬렉션 업로드 실패: {e}")
    
    # Exercise 컬렉션 처리
    if exercise_points:
        print(f"\n[Exercise 컬렉션] {len(exercise_points)}개 항목 처리 중...")
        ensure_collection(qdrant_client, QDRANT_COLLECTION_EXERCISE, vector_size)
        try:
            qdrant_client.upsert(
                collection_name=QDRANT_COLLECTION_EXERCISE,
                points=exercise_points
            )
            print(f"Exercise 컬렉션에 {len(exercise_points)}개 항목 업로드 완료")
        except Exception as e:
            print(f"Exercise 컬렉션 업로드 실패: {e}")
    
    print("\nRAG 데이터 초기화 완료!")


if __name__ == "__main__":
    main()

