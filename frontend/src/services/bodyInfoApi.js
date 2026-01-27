import fetchAPI from "./api.js";

const BASE_URL = "/member-body-info";

// ✅ 로컬 스토리지에서 토큰을 가져오는 헬퍼 함수
const getAuthHeaders = () => {
  const token = localStorage.getItem("accessToken");
  const headers = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
};

/**
 * 1. 신체 정보 조회 (History)
 */
export const getBodyInfoHistory = async (memberId) => {
  try {
    // headers 옵션 추가
    const response = await fetchAPI(`${BASE_URL}/history/${memberId}`, {
      method: 'GET',
      headers: getAuthHeaders(), // ✅ 토큰 포함
    });
    return response || [];
  } catch (error) {
    console.error(`신체 정보 이력 조회 실패 (ID: ${memberId}):`, error);
    throw error;
  }
};

/**
 * 2. 신체 정보 생성
 */
export const createBodyInfo = async (data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}`, {
      method: "POST",
      headers: getAuthHeaders(), // ✅ 토큰 포함
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    console.error("신체 정보 생성 실패:", error);
    throw error;
  }
};

/**
 * 3. 신체 정보 수정
 */
export const updateBodyInfo = async (id, data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}`, {
      method: "PUT",
      headers: getAuthHeaders(), // ✅ 토큰 포함
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    console.error(`신체 정보 수정 실패 (ID: ${id}):`, error);
    throw error;
  }
};

/**
 * 4. 신체 정보 삭제
 */
export const deleteBodyInfo = async (id) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}`, {
      method: "DELETE",
      headers: getAuthHeaders(), // ✅ 토큰 포함
    });
    return response;
  } catch (error) {
    console.error(`신체 정보 삭제 실패 (ID: ${id}):`, error);
    throw error;
  }
};