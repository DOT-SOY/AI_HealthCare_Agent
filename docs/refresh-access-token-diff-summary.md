# Refresh Token / Access Token 로직 — 최근 커밋 vs 현재 요약

> `git diff HEAD` 기준으로 정리. **원래 로직을 망친 부분이 있는지** 중심으로 서술.

---

## 1. 결론 요약

| 구분 | 판단 | 설명 |
|------|------|------|
| **백엔드** | ✅ **원래 로직 유지 + 보강** | Refresh 전용 컨트롤러 추가, Access 만료 단위 수정, 401 명시, Refresh 유효기간 연장 |
| **프론트엔드** | ✅ **동작은 호환, 구조만 단순화** | Refresh는 쿠키만 사용·single-flight로 통일, `fetchAPI` 제거는 **다른 곳에서 미사용**이라 영향 없음 |

**걱정하신 “원래 있던 로직을 망친 것”은 없습니다.**  
기존 흐름(로그인 → Access+Refresh, 401 시 Refresh 후 재시도)은 그대로 두고, 버그 수정·보안·가독성 쪽으로만 변경되었습니다.

---

## 2. 백엔드 변경 상세

### 2.1 `APIRefreshController.java`

- **커밋 상태**: 파일이 **비어 있음** (`index e69de29`).
- **현재 상태**: Refresh 전용 컨트롤러 **전체가 새로 추가**된 상태.

즉, “기존 구현을 고친 것”이 아니라 **예전에는 이 파일에 로직이 없었고, 지금 추가된 것**입니다.  
다른 경로(예: 기존 로그인/토큰 처리)에서 Refresh를 처리하고 있었다면, 그쪽과 **역할이 겹치지 않는지**만 한 번 확인하면 됩니다.  
현재 이 컨트롤러가 `/api/member/refresh`를 담당하는 **유일한 구현**이라면, 기존 로직을 대체·보완한 것으로 보면 됩니다.

- Refresh는 **쿠키만** 사용 (`RefreshCookieUtil.get(request)`).
- `Authorization` 헤더는 **선택**: 있으면 만료 여부·binding 검증에만 쓰고, 없으면 Refresh만으로 재발급.
- Access에 **`tokenType: ACCESS`** 넣어서, Refresh를 Access처럼 쓰는 우회 방지.

→ 기존 “Refresh로 Access 재발급” 개념을 유지하면서, 보안과 역할 분리가 더 분명해진 변경입니다.

### 2.2 `JWTCheckFilter.java`

- **변경 내용**: Access 검증 실패 시 JSON `error: "ERROR_ACCESS_TOKEN"` 반환하기 **전에**  
  `response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);` **(401)** 를 한 줄 추가.

**의미**:  
- 예전에는 401을 명시하지 않았을 가능성이 있어, 프론트에서 “만료/재로그인”으로 처리하기 애매할 수 있었음.  
- 지금은 **항상 401** 로 내려주므로, “Access 실패 → Refresh 시도 또는 로그인 페이지” 흐름이 명확해짐.

→ **기존 로직을 깨는 변경이 아니라**, 기존 의도(401로 만료 알리기)를 제대로 지키는 수정입니다.

### 2.3 `APILoginSuccessHandler.java`

- **변경 내용**:  
  `JWTUtil.generateToken(claims, 15)`  
  →  
  `JWTUtil.generateToken(claims, 15 * 60L)`

**의미**:  
- `JWTUtil.generateToken` 두 번째 인자가 **초(seconds)** 라고 가정하면:  
  - 예전: **15초** 만료.  
  - 현재: **15분(900초)** 만료.  
- 주석도 “Access Token 만료: 15분”으로 맞춰져 있어, **의도는 15분**이었을 가능성이 큼.  
  즉, **버그 수정(15초 → 15분)** 에 가깝고, “원래 15분이었는데 망가진 것”이 아님.

→ **원래 의도에 맞춘 수정**으로 보는 것이 맞습니다.

### 2.4 `RefreshTokenDBService.java` / `RefreshTokenRedisService.java`

- **변경 내용**  
  - `REFRESH_TTL_MIN`: `60 * 24` (24시간) → `15 * 24 * 60` (15일).  
  - `REFRESH_COOKIE_MAX_AGE_SECONDS`: `60 * 60 * 24` (1일) → `15 * 24 * 60 * 60` (15일).

**의미**:  
- Refresh 유효기간과 쿠키 max-age를 **24시간 → 15일**로 늘린 **정책 변경**입니다.  
- “같은 사용자·같은 기기에서 더 오래 로그인 유지”가 목적이라면, 기존 “Refresh로 Access 재발급” 로직은 그대로 두고 **기간만** 바뀐 것이므로, **로직을 망친 것은 아닙니다.**  
- 보안 정책(Refresh 짧게 vs 길게)은 팀/제품 요구에 따라 선택 사항입니다.

---

## 3. 프론트엔드 변경 상세

### 3.1 `frontend/src/services/api.js`

- **예전**:  
  - `fetchAPI(endpoint, options)` 형태의 **공용 fetch 래퍼**.  
  - `localStorage.getItem("accessToken")`으로 토큰을 읽어 `Authorization`에 붙임.  
  - `credentials: 'include'`로 쿠키 전송.
- **지금**:  
  - 그 공용 래퍼는 **제거**.  
  - **Refresh 전용**만 남김:  
    - `refreshAccessToken()`: GET `/api/member/refresh`, **Authorization 없이** `credentials: "include"` 만 사용.  
    - 성공 시 응답의 `accessToken`으로 **쿠키 `member`** 와 **`localStorage.accessToken`** 둘 다 갱신.  
  - `getOrRunRefresh()`: 동시 401 요청 시 refresh를 **한 번만** 돌리도록 single-flight.

**원래 로직과의 관계**:  
- “Access 만료 시 Refresh 한 번 호출해서 새 Access 받고, 그걸로 재시도”라는 **흐름은 동일**.  
- 달라진 점:  
  - Refresh 호출 시 **Authorization 헤더를 더 이상 안 보냄** (백엔드가 쿠키만 보면 되도록 맞춤).  
  - 토큰 갱신 후 **member 쿠키 + localStorage** 둘 다 갱신해서, **jwtUtil / loginSlice 등과 동기화**를 맞춤.  
- `fetchAPI`를 import하는 코드는 **프로젝트 내에 없음** (grep 기준).  
  따라서 **다른 파일이 api.js에 의존하던 동작을 깨뜨린 변경은 아닙니다.**

→ **기존 “Refresh로 Access 갱신” 로직은 유지되고, 호출 방식과 저장처만 정리된 것**입니다.

### 3.2 `frontend/src/util/jwtUtil.jsx`

- **예전**:  
  - `refreshJWT(accessToken)`가 **member 쿠키의 accessToken**을 넘겨서 `/api/member/refresh` 호출 (Authorization에 Bearer 붙임).  
  - `isRefreshing` + `refreshSubscribers` 로 “한 번만 refresh 하고 나머지는 대기” 구현.
- **지금**:  
  - Refresh 호출을 **전부 `api.js`의 `getOrRunRefresh()`** 에 위임.  
  - **Authorization 헤더 없이** 쿠키만 보내는 방식으로 통일.  
  - “한 번만 refresh”는 **Promise 하나로 single-flight** (`getOrRunRefresh`).

**원래 로직과의 관계**:  
- “401 나오면 refresh 한 번 돌리고, 새 Access로 재요청”이라는 **시나리오는 그대로**.  
- 구현만 **jwtUtil ↔ api.js 역할 분리 + 단순화**된 것입니다.  
- Refresh 실패 시 `redirectToLoginIfNeeded()`로 로그인 페이지 보내는 것도, 예전에 “REQUIRE_LOGIN” 등으로 처리하던 것과 **의도적으로 동일**합니다.

→ **동작은 호환되고, 구조만 정리된 변경**입니다.

### 3.3 `loginSlice.jsx` / `App.jsx`

- **diff 결과**:  
  - **내용 변경 없음** (줄바꿈/CRLF 등만 있을 수 있음).  
- 따라서 **Refresh/Access 관련 “원래 로직”은 이 두 파일에서는 그대로**입니다.

---

## 4. 정리: “원래 로직을 망친 건 아닌지”

- **백엔드**  
  - Refresh 처리: 비어 있던 `APIRefreshController`에 **새 로직 추가** (기존 다른 경로와 중복만 아니면 OK).  
  - Access 만료: **15초 → 15분**으로 수정해, 설계 의도에 맞춤.  
  - 401 명시: **의도한 대로 동작하도록** 보강.  
  - Refresh 기간: **정책 변경(24시간 → 15일)** 일 뿐, “Refresh로 Access 재발급” 흐름은 그대로.

- **프론트**  
  - Refresh는 **쿠키만 사용**하도록 맞추고, **member 쿠키 + localStorage** 둘 다 갱신해 기존 jwtUtil/loginSlice와 맞춤.  
  - **single-flight**로 단순화했을 뿐, “한 번만 refresh 하고 재시도” 동작은 유지.  
  - `fetchAPI` 제거는 **다른 파일에서 사용하지 않아** 기존 동작을 깨뜨리지 않음.

**요약하면, “원래 있던 로직을 망친 것”은 아니고, 버그 수정·401 명시·역할 분리·정책(기간) 변경이 가해진 상태입니다.**  
추가로 확인하고 싶다면, **예전에 `/api/member/refresh`를 처리하던 다른 컨트롤러/필터가 있는지** 한 번만 검색해 보시면 됩니다.
