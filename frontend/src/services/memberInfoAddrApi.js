import fetchAPI from "./api.js";

const BASE_URL = "/member-addr-info";

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
 * [조회] 배송지 목록 조회
 */
export const getMemberInfoAddrList = async (memberId) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/member/${memberId}`, {
      method: "GET",
      headers: getAuthHeaders(),
    });
    return response || [];
  } catch (error) {
    console.error("배송지 목록 조회 실패:", error);
    throw error;
  }
};

/**
 * [생성] 배송지 생성
 */
export const createMemberInfoAddr = async (data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}`, {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    console.error("배송지 생성 실패:", error);
    throw error;
  }
};

/**
 * [수정] 배송지 수정
 */
export const updateMemberInfoAddr = async (id, data) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}`, {
      method: "PUT",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    });
    return response;
  } catch (error) {
    console.error(`배송지 수정 실패 (ID: ${id}):`, error);
    throw error;
  }
};

/**
 * [수정] 기본 배송지 설정
 */
export const setDefaultMemberInfoAddr = async (id) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}/default`, {
      method: "PUT",
      headers: getAuthHeaders(),
    });
    return response;
  } catch (error) {
    console.error(`기본 배송지 설정 실패 (ID: ${id}):`, error);
    throw error;
  }
};

/**
 * [삭제] 배송지 삭제
 */
export const deleteMemberInfoAddr = async (id) => {
  try {
    const response = await fetchAPI(`${BASE_URL}/${id}`, {
      method: "DELETE",
      headers: getAuthHeaders(),
    });
    return response;
  } catch (error) {
    console.error(`배송지 삭제 실패 (ID: ${id}):`, error);
    throw error;
  }
};

