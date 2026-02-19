![logo](https://github.com/user-attachments/assets/d0138bba-95c6-40f3-add3-69233df0fdd8)

## 📌 프로젝트 소개

알고리짐(Algorhythm)은 운동과 건강 관리 과정에서 발생하는 불필요한 조작과 사용자 경험의 단절 문제를 해결하기 위해 개발된 헬스케어 웹 서비스입니다.

기존의 운동 및 식단 관리 서비스는 운동 중 스마트폰 조작, 수동 입력, 반복적인 화면 전환을 요구하며 사용자의 집중을 방해합니다. 이러한 구조는 운동의 흐름을 끊고 관리에 대한 피로도를 높여, 장기적인 서비스 사용을 어렵게 만듭니다.

본 프로젝트는 사용자의 행동 흐름을 방해하지 않는 사용자 경험 설계를 핵심 목표로 합니다. 사용자가 운동과 건강 관리 과정에 집중하는 동안, 기록과 관리 과정은 자연스럽게 이루어지도록 서비스 구조를 설계하였습니다.

알고리짐은 사용자가 서비스 조작에 신경 쓰지 않고 본질적인 활동에 몰입할 수 있는 건강 관리 환경을 제공하고자 합니다.

---

## 📖 프로젝트 개요

알고리짐은 음성 기반 인터페이스와 인공지능 기술을 활용하여 건강 관리 전반을 하나의 흐름으로 연결하는 통합 헬스케어 플랫폼입니다.

본 프로젝트는 운동, 식단, 신체 상태 관리 과정에서 반복적으로 발생하는 입력과 조작 단계를 최소화하는 데 초점을 둡니다. 이를 통해 사용자는 관리 행위 자체에 대한 부담을 줄이고, 일상 속에서 지속 가능한 건강 관리 습관을 형성할 수 있습니다.

알고리짐은 단순한 기록 도구가 아닌, 사용자의 행동 맥락과 흐름을 고려한 서비스 구조를 기반으로 설계되었습니다. 사용자의 개입을 최소화하면서도 체계적인 관리가 가능하도록 하는 것이 본 프로젝트의 핵심 방향입니다.

---

# 📆 개발 기간
26.01.19(월) ~ 26.02.13(금)


---

## 🤝 팀원

|                               (팀장)[한정연](https://github.com/DOT-SOY)                                |                                 [김민식](https://github.com/minsik321)                                  |                                        [박태오](https://github.com/teomichaelpark-glitch)                                         |                                 [오인준](https://github.com/01nJun)                                 |                 [박건영](https://github.com/keonyeong4550/one-of-team-project-20251217-20260116)                  |
| :-------------------------------------------------------------------------------------------------------: | :---------------------------------------------------------------------------------------------------: | :-------------------------------------------------------------------------------------------------------------------------------: | :-------------------------------------------------------------------------------------------------: | :---------------------------------------------------------------------------------------------------------------: |
| [<img src="https://avatars.githubusercontent.com/DOT-SOY" width="160" />](https://github.com/DOT-SOY) | [<img src="https://avatars.githubusercontent.com/minsik321" width="160" />](https://github.com/minsik321) | [<img src="https://avatars.githubusercontent.com/teomichaelpark-glitch" width="160" />](https://github.com/teomichaelpark-glitch) | [<img src="https://avatars.githubusercontent.com/01nJun" width="160" />](https://github.com/01nJun) | [<img src="https://avatars.githubusercontent.com/keonyeong4550" width="160" />](https://github.com/keonyeong4550) |


## 🚀 주요 기능 (Key Features)

### 1. 전역 AI 에이전트 및 중앙 오케스트레이션 (Global AI Orchestrator)
- **Centralized AI Hub**: 프로젝트의 모든 AI 기능(운동, 식단, 커머스)을 하나로 연결하는 전역 AI 에이전트를 도입하여 단일 진입점 제공
- **Intent Analysis & Routing**: 사용자의 자연어 발화에서 의도(Intent)를 정밀 분석(Speech Act)하여 적합한 도메인 AI(운동 코치, 영양사, 쇼핑 어시스턴트)로 자동 연결
- **Seamless UX**: 별도의 메뉴 이동 없이 전역 AI와의 대화만으로 루틴 수정, 식단 기록, 상품 구매 등 프로젝트의 모든 기능을 수행 가능

### 2. 문맥 인식형 고도화 RAG (Context-Aware RAG)
- **Hyper-Personalization**: 단순 데이터 검색을 넘어, 사용자의 과거 통증 로그, 운동 수행 이력, 선호도를 벡터 DB(Vector DB)에서 추출하여 LLM에 문맥(Context)으로 주입
- **Dynamic Context Injection**: 사용자의 현재 상태(부상 부위, 수행 능력)에 따라 실시간으로 최적화된 답변과 솔루션을 생성하는 동적 프롬프팅 기술 적용
- **Knowledge Integration**: 전문 운동 지식과 영양학적 데이터를 벡터화하여 할루시네이션(Hallucination)을 최소화한 신뢰도 높은 전문 상담 제공

### 3. 지능형 운동 루틴 관리 및 분석 (Intelligent Workout System)
- **Volume Analysis**: 운동 볼륨(Weight × Reps) 데이터를 기반으로 지난 주/달 대비 성장을 시각적으로 분석하고 과부하 원칙에 따른 루틴 제안
- **Adaptive Routine Generation**: 통증 부위나 컨디션 난조 발생 시, RAG를 통해 해당 부위를 배제하거나 대체할 수 있는 운동을 즉각적으로 추천 및 루틴 수정
- **Interactive Feedback**: 운동 완료 후 서버가 먼저 사용자에게 피드백을 요청하고, 답변에 따라 다음 운동 강도를 조절하는 능동형 코칭 시스템

### 4. 로컬 기반 온디바이스 AI (On-Device AI)
- **Local Inference First**: MediaPipe 등을 활용하여 서버 전송 없이 로컬 기기에서 비전 인식 및 임베딩을 처리, 속도 최적화 및 개인정보 보호
- **Hands-free Control**: 운동 중 터치가 어려운 환경을 고려한 STT(음성 인식) 기반 제어 및 능동형 피드백 시스템

### 5. 식단 관리 시스템 (Meal Management System)
- **Nutrient Dashboard Visualization** : 일일 영양소 목표 달성률 시각화, 끼니별 카드 UI, 식사 완료/생략 토글.
- **Auto-Rebalancing Logic** : 끼니 생략 시 남은 끼니에 영양소를 자동으로 재분배하는 로직 구현.
- **Real-Time Synchronization** : WebSocket을 도입하여 식단 변동 내역 및 수정 사항 실시간 반영.
- **Context-Aware AI Planning** : 사용자 프로필(신체정보, 운동목적) 기반 맞춤형 식단 자동 생성 및 컨텍스트 리셋.

### 6. 비전 AI 식단 및 건강 데이터 OCR (Vision & OCR)
- **Food Lens**: 음식 사진 촬영 시 Vision AI가 종류와 영양소를 분석하여 자동 등록하며, 끼니 누락 시 남은 끼니에 영양소를 재분배(Rebalancing)
- **High-Precision OCR**: GPT-4o Vision과 Tight Cropping을 결합하여 InBody 결과지를 정밀 분석하고, 검증 플로우를 거쳐 구조화된 데이터로 저장

### 7. RAG 기반 대화형 AI 커머스 (RAG-based Conversational Commerce)
- **Safe AI Shopping Assistant**: 상품 정보, 구매 정책, 주의사항을 RAG(검색 증강 생성)로 조회하여 할루시네이션 없이 신뢰도 높은 상품 추천 및 결제 안내 제공
- **Conversational Commerce UX**: 상품 추천부터 장바구니 담기, 결제 유도까지 모든 과정이 대화 흐름 안에서 자연스럽게 이루어지는 AI 커머스 경험 제공
- **Full-Cycle Commerce Integration**: 대화형 인터페이스 내에서 상품 검색(QueryDSL), 주문 상태 머신, 배송 관리, 실시간 랭킹(Redis) 등 쇼핑몰 핵심 비즈니스 로직을 유기적으로 연결


### 8. 보안 인증 및 인프라 (Security & Infrastructure)
- **Robust Security**: JWT 기반 인증/인가 및 Redis를 활용한 RTR(Refresh Token Rotation) 정책으로 토큰 탈취 방지
- **Account Protection**: 로그인 실패 시 Redis TTL을 활용한 계정 잠금(Lock) 정책을 적용하여 무차별 대입(Brute-Force) 공격 차단
- **Scalable Architecture**: 도메인별 모듈화 설계 및 WebSocket을 통한 실시간 양방향 통신 인프라 구축


---

## 🧑‍💻 팀원별 담당 기능

### 💬 한정연 (팀장)
- **커머스 도메인 총괄**: 상품(QueryDSL 동적 검색), 주문, 결제(PG 연동), 리뷰 등 쇼핑몰 전반의 비즈니스 로직 설계
- **프론트엔드 아키텍처**: Vite + React 기반의 성능 최적화 환경 구축 및 디자인 시스템 통합
- **고성능 데이터 처리**: Redis ZSet을 활용한 실시간 판매 랭킹 집계 및 세션 기반 장바구니 병합(회원/비회원) 전략 수립
- **AI 쇼핑 어시스턴트**: 사용자의 질문을 분석해 상품을 추천하고 결제로 유도하는 커머스 전용 RAG 대화 모델 구현

---

### 🧭 김민식 
- **Global AI Architecture & Routing** : 의도(Intent) 분석 및 CLIP 기반 멀티모달 라우팅을 통해 업로드된 이미지가 음식인지 InBody 결과지인지 임베딩으로 판별하여 적절한 AI 서비스로 연결
- **Edge AI & Vision** : MediaPipe 기반 로컬 비전 인식 시스템 구축 및 서버 부하를 최소화한 온디바이스(On-Device) 로직 구현
- **운동 분석 로직** : 운동 볼륨(Weight × Reps) 비교 분석 알고리즘 및 통증 로그와 연계된 운동 추천 RAG 설계
- **인터랙티브 UX** : WebSocket을 활용해 AI가 먼저 말을 거는(Proactive) 대화형 UX 및 STT 핸즈프리 제어 기능 개발


---

### 🤖 박태오
- **AI 식단 분석**: Vision AI를 활용한 음식 이미지 자동 인식 및 자연어 의도 파악을 통한 식단 CRUD 자동화
- **영양소 알고리즘**: 사용자 프로필 기반 맞춤형 식단 생성 및 끼니 누락 시 영양소 자동 재분배(Rebalancing) 로직 구현
- **OCR 백엔드 로직**: GPT-4o Vision을 활용한 다중 이미지 병렬 처리 및 InBody 데이터 필드별 정밀 추출 파이프라인
- **실시간 대시보드**: WebSocket 기반 식단 변경 사항 실시간 동기화 및 영양 섭취 현황 트래킹 기능

---

### 🧩 오인준
- **보안 및 인증**: JWT 액세스/리프레시 토큰 관리, 카카오 소셜 로그인 통합, Redis 기반 계정 잠금(Brute-force 방지) 구현
- **RAG 파이프라인**: Qdrant 벡터 DB를 활용하여 운동 지식 및 통증 데이터를 검색(Retrieval)하고 생성(Generation)하는 핵심 로직
- **운동 AI 백엔드**: 사용자의 운동 의도(Intent)를 분류하여 루틴 추천, 수정, 대체 운동 제안을 수행하는 AI 서버 연동
- **백오피스 시스템**: 관리자(Admin) 전용 주문 관리, 배송 상태 변경, 사용자 권한 제어(ACL) 시스템 개발

---

### 📄 박건영
- **건강 데이터 시각화**: Recharts를 활용한 체성분(골격근, 체지방 등) 시계열 데이터 정규화 및 반응형 차트 구현
- **OCR 사용자 플로우**: 이미지 업로드부터 데이터 분석, 검증, 저장까지 이어지는 멀티 모달(Multi-Modal) UX 설계
- **프로필 및 배송 관리**: 신체 정보 수정과 배송지 CRUD가 통합된 복합 모달 인터페이스 및 데이터 동기화 로직 구현
---

### 📚 사용 스택

<div>
  <!-- Frontend -->
  <img src="https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB">
  <img src="https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white">
  <img src="https://img.shields.io/badge/Redux_Toolkit-593D88?style=for-the-badge&logo=redux&logoColor=white">
  <img src="https://img.shields.io/badge/TailwindCSS-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white">
  <br/>

  <!-- Backend -->
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/WebSocket-010101?style=for-the-badge&logo=socket.io&logoColor=white">
  <br/>

  <!-- Database & Cache -->
  <img src="https://img.shields.io/badge/MariaDB-003545?style=for-the-badge&logo=mariadb&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">
  <br/>

  <!-- AI -->
  <img src="https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white">
  <img src="https://img.shields.io/badge/OpenAI-412991?style=for-the-badge&logo=openai&logoColor=white">
  <img src="https://img.shields.io/badge/Qdrant-FF4F8B?style=for-the-badge&logo=vectorworks&logoColor=white">
  <img src="https://img.shields.io/badge/Gemini-4285F4?style=for-the-badge&logo=google&logoColor=white">
  <br/><br/>
</div>

---

### 🎨 Frontend
- React 19 + Vite 7
- Redux Toolkit (전역 상태 관리)
- Tailwind CSS
- WebSocket 통신 (@stomp/stompjs, STOMP)
- MediaPipe (Pose 기반 동작 인식)
- Recharts, GSAP, lucide-react
- TossPayments (결제 연동)
- dayjs
- HTML5, JavaScript (JSX)

---

### ⚙️ Backend
- Spring Boot 3.5.9
- Java 21
- Spring Security + JWT 인증
- WebSocket / STOMP 기반 실시간 통신
- WebFlux (AI 서버 비동기 호출)
- QueryDSL

---

### 🗄️ Database & Cache
- MariaDB (메인 데이터베이스)
- Redis (Refresh Token 관리 등)

---

### 🤖 AI Server (별도 프로세스)
- FastAPI
- OpenAI API (채팅, 임베딩 등)
- Qdrant (Vector DB, RAG 구성 / Docker)
- RAG 기반 운동 지식 검색 및 추천
- Gemini (이미지 분류)
- OCR (InBody 이미지 업로드 및 검증)

---
### <img width="30" height="30" alt="jenkins_icon-cutout" src="https://github.com/user-attachments/assets/37235621-0f9e-4d3b-90a0-f7a87a798ff4" /> Deployment 
 - AWS deploy
 - NGINX deploy
 - JenKins CI/CD, Auto Deploy
 - PUTTY, PUTTY GEN, PPK, DEM

| JenKins Deploy Flow |
| :--: | 
| <img width="800" alt="jenkins-ci cd" src="https://github.com/user-attachments/assets/f7954d7f-4fa6-48d2-bc20-ba42b3eabc71" /> | 

---

## 🎥 [유튜브 시연영상 링크 바로보기](https://youtu.be/BuGlxz3XMa8?si=u5EC4rfaLf9PgRS7)

## 📋 [Fullstack PDF 바로 보기](https://dot-soy.github.io/woometan/fullstack_pdf.html)
## 📋 [AI PDF 바로 보기](https://dot-soy.github.io/woometan/fullstack_pdf_ai.html)

# ERD 구조

<img width="8192" height="7897" alt="ERD" src="https://github.com/user-attachments/assets/1dc5772c-ea44-4d6a-b655-9eb37ab739c7" />

# 계층구조

![계층구조](https://github.com/user-attachments/assets/08d7ecaf-c42a-4031-82e0-e97b83fc7045)


---
## ⚡ 성능 개선 

### 🔐 Refresh Token 저장소 성능 비교 (12,000건 기준)

| 저장소 | 평균 처리 시간 | 결과 |
|------|--------------|------|
| Redis | **34 ms** | ✅ 가장 빠름 |
| DB | 119 ms | ❌ 상대적으로 느림 |

- Redis가 DB 대비 **약 3.5배 빠른 처리 성능**
- 메모리 기반 접근으로 디스크 I/O 제거
- TTL 기반 자동 만료 지원 → 운영 부담 최소화

> 🔎 **결론**: JWT Refresh Token 관리에는 Redis가 가장 효율적

---

### 💬 채팅 무한 스크롤 성능 최적화 (10,000건 기준)

| 항목 | 적용 전 | 적용 후 |
|----|-------|-------|
| 메시지 로드 방식 | 전체 로드 | 분할 로드 (20개) |
| Total Time | 18,193 ms | **5,134 ms** |
| Scripting Time | 6,805 ms | **282 ms** |
| Rendering Time | 3,304 ms | **47 ms** |
| DOM Nodes | 23,655 | **224** |

- DOM 개수 **약 99% 감소**
- JS 실행 시간 **약 96% 감소**
- 대량 메시지 렌더링 시 발생하던 끊김 현상 제거

> 🔎 **결론**: 무한 스크롤 + 가상 스크롤 적용으로 대규모 채팅에서도 안정적인 UX 확보

---

## 🎬 서비스 주요 기능 (GIF)

## 📱 메인 페이지 반응형 UI & 실시간 채팅

![메인 페이지 및 반응형](https://github.com/user-attachments/assets/0f178bd1-f56f-48c7-927e-3baeff4bf741)

## 🔐 인증 시스템

![로그인   소셜 로그인](https://github.com/user-attachments/assets/7a590231-e7de-4d0f-84c7-bd20bc47b6ef)

## 🤖 대화형 AI 채팅

![전역 ai](https://github.com/user-attachments/assets/fd022017-f5c8-46f5-a06a-7f56ba7ba20f)

## 🏋️ 운동 분석 & 관리

https://github.com/user-attachments/assets/cd1458fe-ca19-4111-839d-79da8ab9e326


## 🗂️ 기록 & 루틴 관리

![루틴 페이지](https://github.com/user-attachments/assets/16cb23a6-5182-4e2d-80ac-5e3135627531)

![기록 페이지](https://github.com/user-attachments/assets/7a716824-69b8-4870-b8ff-72365388e9a6)


## 🍽️ 식단 관리

![식단 페이지](https://github.com/user-attachments/assets/8b3b6851-9b6a-461d-999e-8df6d7055132)


## 👤 마이페이지

![마이 페이지( 인바디, 배송지 수정 )](https://github.com/user-attachments/assets/52e810fe-acc4-4e09-978e-130dfefe3ed6)

## 🛒 AI 커머스
![img1](https://i.imgur.com/BOk2k4i.gif)
![img2](https://i.imgur.com/vOzPkCc.gif)



---
<br>


# 🛠️ 설치 및 실행 방법 (전체)

이 문서는 **프론트엔드 + 백엔드 + Python AI 서버 + Qdrant(RAG)** 로 구성된 프로젝트의 로컬 실행 방법을 정리한 가이드입니다.

---

## 0. 사전 요구사항

다음 환경이 미리 준비되어 있어야 합니다.

* Node.js **LTS 버전 권장**
* Java **21**
* Python **3.10 이상**
* MariaDB 실행 중
* Redis 실행 중
* Docker (Qdrant 사용 시)

---

## 1. 환경 변수 설정 (.env)

각 디렉터리에 `.env` 파일을 생성하여 사용합니다.

> ⚠️ `.env` 파일은 **절대 Git에 커밋하지 말고**, 로컬에서만 관리하세요.

### 📁 위치별 설명

| 위치               | 설명                                    |
| ---------------- | ------------------------------------- |
| `frontend/.env`  | 프론트엔드 환경 변수                           |
| `backend/.env`   | 백엔드 환경 변수 (DB, Redis 등)               |
| `ai-server/.env` | Python AI 서버 환경 변수 (OpenAI, Qdrant 등) |

---

### frontend/.env 예시

```env
VITE_API_SERVER_HOST=http://localhost:8080
VITE_KAKAO_REST_API_KEY=''
VITE_KAKAO_REDIRECT_URI=http://localhost:5173/member/kakao
```

---

### backend/.env 예시

```env
TOSS_CLIENT_KEY=''
TOSS_SECRET_KEY=''
TOSS_SUCCESS_URL=http://localhost:5173/pay/success
TOSS_FAIL_URL=http://localhost:5173/pay/fail
```

---

### ai-server/.env 예시

```env
OPENAI_API_KEY=''
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=exercise_knowledge
OPENAI_MODEL=gpt-4o-mini
PAIN_ADVICE_MODEL=gpt-4.1-nano
GEMINI_API_KEY=''
MEAL_GEMINI_MODEL=gemini-2.5-pro
```

---

## 2. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

* 접속 주소: [http://localhost:5173](http://localhost:5173)
* 프록시 설정

  * `/api` → [http://localhost:8080](http://localhost:8080)
  * `/ws` → ws://localhost:8080

---

## 3. 백엔드 실행 (Spring Boot)

```bash
cd backend
```

### Windows

```bash
.\gradlew bootRun
```

### Linux / Mac

```bash
./gradlew bootRun
```

* 실행 포트: **8080**
* DB, Redis 설정은 `application.properties` 기준

---

## 4. Python AI 서버 실행

### 1) 가상환경 및 의존성 설치

```bash
cd ai-server
python -m venv venv
```

#### Windows

```bash
venv\Scripts\activate
```

#### Linux / Mac

```bash
source venv/bin/activate
```

```bash
pip install -r requirements.txt
```

---

### 2) 서버 실행

```bash
uvicorn main:app --reload --port 8000
```

---

## 5. Qdrant 실행 (Vector DB)

Docker로 Qdrant를 실행합니다.

```bash
docker run -d \
  -p 6333:6333 \
  -p 6334:6334 \
  -v qdrant_storage:/qdrant/storage \
  --name qdrant \
  qdrant/qdrant
```

* 대시보드: [http://localhost:6333/dashboard](http://localhost:6333/dashboard)

---

## 6. RAG 데이터 초기화

Qdrant가 실행 중인 상태에서 **프로젝트 루트**에서 실행합니다.

```bash
python setup_rag.py
```

* RAG에 사용될 데이터가 Qdrant Vector DB에 적재됩니다.

---

## 7. Qdrant 데이터 복원 (스냅샷 업로드)

1. Qdrant 실행
2. 대시보드 접속

   * [http://localhost:6333/dashboard#/collections](http://localhost:6333/dashboard#/collections)
3. 컬렉션 선택
4. **Upload Snapshot** 버튼 클릭
5. 보관 중인 스냅샷 파일 업로드 (meal_foods, meal_templates)

---

## 8. 전체 실행 순서 (권장)

1. MariaDB 실행
2. Redis 실행
3. `.env` 파일 생성

   * `frontend/.env`
   * `backend/.env`
   * `ai-server/.env`
4. Qdrant 실행 (Docker)
5. RAG 데이터 초기화

   ```bash
   python setup_rag.py
   ```
6. Python AI 서버 실행

   ```bash
   cd ai-server
   uvicorn main:app --reload --port 8000
   ```
7. 백엔드 실행

   ```bash
   cd backend
   .\gradlew bootRun
   ```
8. 프론트엔드 실행

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

---

## ✅ 접속 요약

| 서비스              | 주소                                                                 |
| ---------------- | ------------------------------------------------------------------ |
| Frontend         | [http://localhost:5173](http://localhost:5173)                     |
| Backend API      | [http://localhost:8080](http://localhost:8080)                     |
| AI Server        | [http://localhost:8000](http://localhost:8000)                     |
| Qdrant Dashboard | [http://localhost:6333/dashboard](http://localhost:6333/dashboard) |




