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
 * [수정] 내 신체 정보 이력 조회 (토큰 사용)
 */
export const getMyBodyInfoHistory = async () => {
  try {
    // memberId 없이 호출 (서버가 토큰에서 알아냄)
    const response = await fetchAPI(`${BASE_URL}/history/me`, {
      method: 'GET',
      headers: getAuthHeaders(),
    });
    return response || [];
  } catch (error) {
    console.error("내 신체 정보 조회 실패:", error);
    throw error;
  }
};
/**
 * OCR 결과 저장 후 직전 1 row와 비교하여 규칙 기반 피드백 반환 (7일 식단/운동 없음)
 */
export const saveAndCompare = async (data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/save-and-compare`, {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    console.error("저장 및 비교 실패:", error);
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
//export const updateBodyInfo = async (id, data) => {
//  try {
//    const response = await fetchAPI(`${BASE_URL}/${id}`, {
//      method: "PUT",
//      headers: getAuthHeaders(), // ✅ 토큰 포함
//      body: JSON.stringify(data),
//    });
//    return response;
//  } catch (error) {
//    console.error(`신체 정보 수정 실패 (ID: ${id}):`, error);
//    throw error;
//  }
//};
/**
 * 3. 신체 정보 수정 (디버깅 강화 버전)
 */
export const updateBodyInfo = async (id, data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    // api.js에서 이미 에러 로그를 찍어주므로 여기선 던지기만 하면 됩니다.
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