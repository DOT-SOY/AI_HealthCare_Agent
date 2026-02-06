import { getCookie } from "../util/cookieUtil";

const API_BASE_URL = import.meta.env.VITE_API_SERVER_HOST
  ? `${import.meta.env.VITE_API_SERVER_HOST}/api`
  : "http://localhost:8080/api";

/** AI 서버 주소 (Paddle OCR) */
const AI_SERVER_BASE = import.meta.env.VITE_AI_SERVER_HOST || "http://localhost:8000";

/**
 * [Paddle OCR] 이미지에서 인바디 체성분 텍스트 추출 (AI 서버 호출)
 * @param {File} file - 이미지 파일 (JPEG, PNG 등)
 * @returns {Promise<{ parsed?: object }>}
 */
export const extractOcrTextPaddle = async (file) => {
  const url = `${AI_SERVER_BASE}/ocr/extract`;
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(url, {
    method: "POST",
    body: formData,
    credentials: "omit",
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || data.message || `OCR 요청 실패 (${response.status})`);
  }
  if (data.error) {
    throw new Error(data.error);
  }
  return { parsed: data.parsed ?? {} };
};

/**
 * [기존] 이미지 파일에서 텍스트 추출 (Spring 백엔드 /api/ocr/extract)
 * @param {File} file - 이미지 파일 (JPEG, PNG 등)
 * @returns {Promise<{ parsed?: object }>}
 */
export const extractOcrText = async (file) => {
  const url = `${API_BASE_URL}/ocr/extract`;
  const formData = new FormData();
  formData.append("file", file);

  const memberInfo = getCookie("member");
  const token = memberInfo?.accessToken || localStorage.getItem("accessToken");

  const headers = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    method: "POST",
    headers,
    body: formData,
    credentials: "include",
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || `OCR 요청 실패 (${response.status})`);
  }

  return response.json();
};
