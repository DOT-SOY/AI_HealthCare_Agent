import axios from "axios";
import { getCookie, setCookie } from "./cookieUtil";
const API_SERVER_HOST =
  import.meta.env.VITE_API_SERVER_HOST || "http://localhost:8080";

const jwtAxios = axios.create({ withCredentials: true });


// [추가] 리프레시 진행 상태와 대기열 관리
let isRefreshing = false;
let refreshSubscribers = [];

// 새 토큰이 나오면 대기 중이던 요청들에게 나눠주고 재실행시키는 함수
const onRefreshed = (accessToken) => {
refreshSubscribers.forEach((callback) => callback(accessToken));
refreshSubscribers = [];
};

// 리프레시 중일 때 들어온 요청들을 대기열에 담는 함수
const addRefreshSubscriber = (callback) => {
refreshSubscribers.push(callback);
};

// Access Token 만료 시 Refresh Token으로 새 JWT 발급
// accessToken이 없어도 Refresh Token만으로 재발급 가능하도록 수정
const refreshJWT = async (accessToken) => {
  const host = API_SERVER_HOST;

  // accessToken이 있으면 Authorization 헤더에 포함, 없으면 빈 헤더 (Refresh Token만 사용)
  const headers = accessToken
    ? { Authorization: `Bearer ${accessToken}` }
    : {};

  try {
    console.log("Calling refresh endpoint with headers:", headers);
    const res = await axios.get(`${host}/api/member/refresh`, {
      headers,
      withCredentials: true,
    });

    console.log("Refresh endpoint response status:", res.status);
    return res.data;
  } catch (err) {
    console.error("Refresh endpoint error:", err.response?.status, err.response?.data);
    // 에러 응답 처리
    if (err.response && err.response.data && err.response.data.error) {
      throw err;
    }
    // 네트워크 에러 등
    throw err;
  }
}; // Map.of("accessToken", newAccessToken) 반환 (refresh는 HttpOnly 쿠키로만 관리)

//before request - async로 변경하여 accessToken이 없을 때 Refresh Token으로 재발급 시도
const beforeReq = async (config) => {
  console.log("before request.............");
  const memberInfoRaw = getCookie("member");
  // 쿠키에서 member 조회, 없으면 로그인 필요 에러 반환
  if (!memberInfoRaw) {
    console.log("Member NOT FOUND");
    return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
  }

  // getCookie가 이미 객체를 준다면 바로 구조분해 할당
  const { accessToken } = memberInfoRaw;

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  } else {
    // 토큰이 없어도 일단 보냅니다. (어차피 인터셉터에서 잡기 때문에)
    console.log("No accessToken found in cookie, proceeding to let interceptor handle it.");
  }

//  console.log("accessToken:", accessToken);

  // Authorization 헤더 설정
  config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
};

//fail request
const requestFail = (err) => {
  console.log("request error............");

  return Promise.reject(err);
};

// 서버에서 ERROR_ACCESS_TOKEN 반환 → 토큰 만료, refreshJWT 호출 → 새로운 JWT 발급, 쿠키 갱신
//before return response
const beforeRes = async (res) => {
  console.log("before return response...........");
  //'ERROR_ACCESS_TOKEN'
  const data = res.data;

  // 백엔드에서 'ERROR_ACCESS_TOKEN' 에러가 왔을 때
  if (data && data.error === "ERROR_ACCESS_TOKEN") {
    // 이전에 이미 재시도한 요청인지 확인 (무한 루프 방지)
    const originalRequest = res.config;

    if (originalRequest._retry) {
      return Promise.reject({ response: { data: { error: "Login Failed" } } });
    }

   // 이미 다른 요청이 리프레시 중이라면 대기열(Promise)에서 기다림
    if (isRefreshing) {
      return new Promise((resolve) => {
        addRefreshSubscriber((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(axios(originalRequest)); // 새 토큰으로 재실행
        });
      });
    }

    // 내가 첫 번째로 리프레시를 시작하는 요청일 때
    isRefreshing = true;
    originalRequest._retry = true;

    try {
      const memberCookieValueRaw = getCookie("member");

      if (!memberCookieValueRaw) {
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }

      // 쿠키 파싱
      let memberCookieValue;
      try {
        memberCookieValue = typeof memberCookieValueRaw === 'string' ? JSON.parse(memberCookieValueRaw) : memberCookieValueRaw;
      } catch (e) {
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }

      if (!memberCookieValue || !memberCookieValue.accessToken) {
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }

      // 토큰 갱신 시도
//      console.log("토큰 갱신 시도");
      const result = await refreshJWT(memberCookieValue.accessToken);
      const newAccessToken = result.accessToken;

      // 쿠키 갱신
//      console.log("쿠키 갱신");
      memberCookieValue.accessToken = newAccessToken;
      setCookie("member", JSON.stringify(memberCookieValue), 1);

      // 리프레시 완료 신호를 보내고 대기자들 출발시킴
      isRefreshing = false;
      onRefreshed(newAccessToken);

      // 갱신된 토큰으로 원래 요청의 헤더 수정
//      console.log("갱신된 토큰으로 원래 요청의 헤더 수정");
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

      // 원래 요청 재실행
//      console.log("원래 요청 재실행");
      return await axios(originalRequest);
    } catch (err) {
      isRefreshing = false;
      console.log("Refresh Token Expired or Error");
      return Promise.reject(err);
    }
  }

  return res;
};

//fail response
const responseFail = async (err) => {
  // 에러 응답 처리
  console.log("response fail error.............");
  if (err.response && err.response.data) {
    const errorData = err.response.data;
    const errorType = errorData.error;
    const originalRequest = err.config;

    // Refresh Token 관련 에러는 로그인 필요로 처리
    if (errorType === "UNKNOWN_REFRESH" || errorType === "NULL_REFRESH" ||
        errorType === "REFRESH_REPLAY_DETECTED" || errorType === "REFRESH_TAMPERED" ||
        errorType === "REFRESH_DEVICE_MISMATCH" || errorType === "REFRESH_IP_MISMATCH" ||
        errorType === "REFRESH_BINDING_MISMATCH" || errorType === "INVALID_REFRESH_CLAIMS") {
      return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
    }

    // ERROR_ACCESS_TOKEN, Expired, MalFormed 등의 JWT 에러 처리
    if (errorType === "ERROR_ACCESS_TOKEN" || errorType === "Expired" || errorType === "MalFormed" || errorType === "Invalid") {
      // 무한 루프 방지
      if (originalRequest && originalRequest._retry) {
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }

      if (originalRequest) {
        // 동시성 제어: 이미 리프레시 중이면 대기
        if (isRefreshing) {
          return new Promise((resolve) => {
            addRefreshSubscriber((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(axios(originalRequest));
            });
          });
        }

        isRefreshing = true;
        originalRequest._retry = true;

        try {
          const memberCookieValueRaw = getCookie("member");

          if (!memberCookieValueRaw) {
            return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
          }

          // 쿠키 파싱
          let memberCookieValue;
          try {
            memberCookieValue = typeof memberCookieValueRaw === 'string' ? JSON.parse(memberCookieValueRaw) : memberCookieValueRaw;
          } catch (e) {
            return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
          }

          // 토큰 갱신 시도
          const currentAccessToken = memberCookieValue?.accessToken || null;
          console.log("Attempting token refresh, current accessToken:", currentAccessToken ? "exists" : "missing");

          const result = await refreshJWT(null);
          const newAccessToken = result.accessToken;

          console.log("Token refresh result:", result);

          if (newAccessToken) {
            // 쿠키 갱신
            if (!memberCookieValue) { memberCookieValue = {}; }
            memberCookieValue.accessToken = newAccessToken;
            setCookie("member", JSON.stringify(memberCookieValue), 1);

            // 대기열 해제
            isRefreshing = false;
            onRefreshed(newAccessToken);

            // 갱신된 토큰으로 원래 요청의 헤더 수정
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

            // 원래 요청 재실행
            return await axios(originalRequest);
          } else {
            isRefreshing = false;
            return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
          }
        } catch (refreshErr) {
          isRefreshing = false;
          console.error("Refresh Token 갱신 실패:", refreshErr.response?.data || refreshErr.message);
          return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
        }
      }
    }
  }

  return Promise.reject(err);
};

jwtAxios.interceptors.request.use(beforeReq, requestFail);

jwtAxios.interceptors.response.use(beforeRes, responseFail);

export default jwtAxios;