import axios from "axios";
import { getCookie } from "./cookieUtil";
import { getOrRunRefresh } from "../services/api.js";

const API_SERVER_HOST =
  import.meta.env.VITE_API_SERVER_HOST || "http://localhost:8080";

const jwtAxios = axios.create({
  baseURL: `${API_SERVER_HOST}/api`,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json; charset=UTF-8",
  },
});

// before request: 토큰이 있으면 Authorization 추가, 없으면 그대로 진행 (공개 API 호출 허용)
const beforeReq = async (config) => {
  const memberInfoRaw = getCookie("member");
  if (memberInfoRaw) {
    let obj;
    try {
      obj = typeof memberInfoRaw === "string" ? JSON.parse(memberInfoRaw) : memberInfoRaw;
    } catch {
      obj = null;
    }
    const accessToken = obj?.accessToken;
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
  }
  return config;
};

//fail request
const requestFail = (err) => {
  console.log("request error............");

  return Promise.reject(err);
};

// 서버에서 TOKEN_EXPIRED 반환 시에만 refresh 시도 (401 중 만료만 재발급, 그 외는 재로그인)
//before return response
const beforeRes = async (res) => {
  console.log("before return response...........");
  const data = res.data;

  // 백엔드에서 'TOKEN_EXPIRED' 일 때만 refresh (api.js getOrRunRefresh)
  if (data && data.error === "TOKEN_EXPIRED") {
    const originalRequest = res.config;

    if (originalRequest._retry) {
      return Promise.reject({ response: { data: { error: "Login Failed" } } });
    }

    try {
      const newAccessToken = await getOrRunRefresh();
      if (!newAccessToken) {
        redirectToLoginIfNeeded();
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }
      originalRequest._retry = true;
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
      return await axios(originalRequest);
    } catch (err) {
      return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
    }
  }

  return res;
};

function redirectToLoginIfNeeded() {
  if (typeof window !== "undefined" && !window.location.pathname.includes("/member/login")) {
    window.location.href = `/member/login?from=${encodeURIComponent(window.location.pathname)}`;
  }
}

//fail response
const responseFail = async (err) => {
  // 에러 응답 처리
  console.log("response fail error.............");
  const status = err.response?.status;
  const errorData = err.response?.data;
  const errorType = errorData?.error;
  const originalRequest = err.config;

  if (err.response && errorData) {
    // Refresh Token 관련 에러는 로그인 필요로 처리
    if (errorType === "UNKNOWN_REFRESH" || errorType === "NULL_REFRESH" ||
        errorType === "REFRESH_REPLAY_DETECTED" || errorType === "REFRESH_TAMPERED" ||
        errorType === "REFRESH_DEVICE_MISMATCH" || errorType === "REFRESH_IP_MISMATCH" ||
        errorType === "REFRESH_BINDING_MISMATCH" || errorType === "INVALID_REFRESH_CLAIMS") {
      redirectToLoginIfNeeded();
      return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
    }
  }

  // 401 중 TOKEN_EXPIRED일 때만 refresh 시도, 그 외(형식 오류/타입 오류/헤더 없음 등)는 재로그인
  if (status === 401 && errorType === "TOKEN_EXPIRED" && originalRequest) {
    if (originalRequest._retry) {
      return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
    }

    try {
      const newAccessToken = await getOrRunRefresh();
      if (!newAccessToken) {
        redirectToLoginIfNeeded();
        return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
      }
      originalRequest._retry = true;
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
      return await axios(originalRequest);
    } catch (refreshErr) {
      redirectToLoginIfNeeded();
      return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
    }
  }

  // 401이지만 TOKEN_EXPIRED가 아니면 재로그인 유도
  if (status === 401) {
    redirectToLoginIfNeeded();
    return Promise.reject({ response: { data: { error: "REQUIRE_LOGIN" } } });
  }

  return Promise.reject(err);
};

jwtAxios.interceptors.request.use(beforeReq, requestFail);

jwtAxios.interceptors.response.use(beforeRes, responseFail);

export default jwtAxios;