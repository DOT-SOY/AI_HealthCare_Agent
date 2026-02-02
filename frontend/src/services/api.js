/**
 * JWT Refresh 전용 모듈
 * - getOrRunRefresh(): jwtUtil(jwtAxios)에서 401 시 공통 사용 (single-flight)
 * - 인증 API 호출은 전부 jwtAxios 사용
 */

import { getCookie, setCookie } from "../util/cookieUtil";

const API_BASE_URL = import.meta.env.VITE_API_SERVER_HOST
  ? `${import.meta.env.VITE_API_SERVER_HOST}/api`
  : "http://localhost:8080/api";

/** refresh 호출 후 쿠키 갱신, 성공 시 새 accessToken 반환 / 실패 시 null */
async function refreshAccessToken() {
  console.log('[API] 토큰 갱신 시도 중...');
  
  // Refresh Token은 HttpOnly 쿠키로 자동 전송됨 (withCredentials: true)
  // Authorization 헤더는 보내지 않음 (백엔드가 쿠키의 Refresh Token만 확인)
  const headers = { "Content-Type": "application/json" };

  try {
    const res = await fetch(`${API_BASE_URL}/member/refresh`, {
      method: "GET",
      headers,
      credentials: "include", // 쿠키 전송 (Refresh Token)
    });
    
    console.log('[API] Refresh 응답 상태:', res.status);
    
    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      console.log('[API] Refresh 실패:', errorData);
      
      // JWT_008: 알 수 없는 Refresh Token → 재로그인 필요
      if (errorData?.code === 'JWT_008' || errorData?.error === 'JWT_008') {
        console.log('[API] Refresh Token 없음 또는 만료 - 재로그인 필요');
      }
      return null;
    }
    
    const data = await res.json().catch(() => ({}));
    const newAccessToken = data?.accessToken ?? null;
    
    if (!newAccessToken) {
      console.log('[API] Refresh 응답에 accessToken 없음');
      return null;
    }

    console.log('[API] 새 토큰 발급 성공');
    
    // 쿠키의 member 객체 업데이트
    const memberRaw = getCookie("member");
    if (memberRaw) {
      try {
        const member = typeof memberRaw === "string" ? JSON.parse(memberRaw) : memberRaw;
        member.accessToken = newAccessToken;
        setCookie("member", JSON.stringify(member), 1);
        console.log('[API] 쿠키 업데이트 완료');
      } catch (err) {
        console.error('[API] 쿠키 업데이트 실패:', err);
      }
    }
    
    // localStorage도 업데이트 (loginSlice와 동기화)
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem('accessToken', newAccessToken);
        console.log('[API] localStorage 업데이트 완료');
      } catch (err) {
        console.error('[API] localStorage 업데이트 실패:', err);
      }
    }
    
    return newAccessToken;
  } catch (err) {
    console.error('[API] Refresh 요청 에러:', err);
    return null;
  }
}

/** Single-flight: 동시에 여러 요청이 401이어도 refresh는 한 번만 호출, 나머지는 같은 Promise 대기 */
let refreshPromise = null;

/**
 * refresh 실행 중이면 그 Promise 반환, 없으면 새로 실행 후 반환.
 * jwtAxios 인터셉터에서 401 시 공통 사용 (Refresh 요청 한 번만).
 */
export function getOrRunRefresh() {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}
