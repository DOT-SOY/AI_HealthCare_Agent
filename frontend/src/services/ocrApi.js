import { getCookie } from "../util/cookieUtil";

const API_BASE_URL = import.meta.env.VITE_API_SERVER_HOST
  ? `${import.meta.env.VITE_API_SERVER_HOST}/api`
  : "http://localhost:8080/api";

/**
 * 이미지 파일에서 텍스트 추출 (OCR)
 * @param {File} file - 이미지 파일 (JPEG, PNG 등)
 * @returns {Promise<{ text: string, language?: string, confidence?: number }>}
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
