/**
 * API 기본 설정 및 유틸리티
 */

const API_BASE_URL = import.meta.env.VITE_API_SERVER_HOST
  ? `${import.meta.env.VITE_API_SERVER_HOST}/api`
  : "http://localhost:8080/api";

/**
 * 기본 fetch 래퍼
 */
async function fetchAPI(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;

  const defaultHeaders = {
    "Content-Type": "application/json",
  };

  // JWT 토큰이 있으면 Authorization 헤더 추가
  const token = localStorage.getItem("accessToken");
  if (token) {
    defaultHeaders["Authorization"] = `Bearer ${token}`;
  }

  const config = {
    ...options,
    headers: {
      ...defaultHeaders,
      ...options.headers,
    },
    // 쿠키 전송을 위해 credentials 추가 (guest_token 쿠키 전송에 필요)
    credentials: 'include',
  };

  try {
    console.log(`[API] 요청 URL: ${url}`);
    console.log(`[API] 요청 옵션:`, { method: options.method || "GET", headers: config.headers });
    
    const response = await fetch(url, config);
    
    console.log(`[API] 응답 상태: ${response.status} ${response.statusText}`);

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const errorMessage = errorData.message || `HTTP error! status: ${response.status}`;
      console.error(`[API] 에러 응답:`, errorData);
      throw new Error(errorMessage);
    }

    // 204 No Content 등은 JSON이 없을 수 있음
    if (response.status === 204) {
      return null;
    }

    const data = await response.json();
    console.log(`[API] 응답 데이터:`, data);
    return data;
  } catch (error) {
    // 네트워크 에러인지 확인
    if (error.name === "TypeError" && error.message.includes("fetch")) {
      console.error("[API] 네트워크 에러 - 백엔드 서버에 연결할 수 없습니다:", error);
      throw new Error("백엔드 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.");
    }
    console.error("[API] 에러:", error);
    throw error;
  }
}

export default fetchAPI;
