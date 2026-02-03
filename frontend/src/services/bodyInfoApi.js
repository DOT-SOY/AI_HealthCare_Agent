import jwtAxios from '../util/jwtUtil';

const BASE_URL = '/member-body-info';

/**
 * [조회] 내 신체 정보 이력 조회 (JWT 인증, jwtAxios가 토큰 자동 첨부)
 */
export const getMyBodyInfoHistory = async () => {
  try {
    const res = await jwtAxios.get(`${BASE_URL}/history/me`);
    return res.data ?? [];
  } catch (error) {
    console.error('내 신체 정보 조회 실패:', error);
    throw error;
  }
};

/**
 * [생성] 신체 정보 생성
 */
export const createBodyInfo = async (data) => {
  try {
    const res = await jwtAxios.post(BASE_URL, data);
    return res.data;
  } catch (error) {
    console.error('신체 정보 생성 실패:', error);
    throw error;
  }
};

/**
 * [수정] 신체 정보 수정
 */
export const updateBodyInfo = async (id, data) => {
  try {
    const res = await jwtAxios.put(`${BASE_URL}/${id}`, data);
    return res.data;
  } catch (error) {
    console.error(`신체 정보 수정 실패 (ID: ${id}):`, error);
    throw error;
  }
};

/**
 * [삭제] 신체 정보 삭제
 */
export const deleteBodyInfo = async (id) => {
  try {
    const res = await jwtAxios.delete(`${BASE_URL}/${id}`);
    return res.data;
  } catch (error) {
    console.error(`신체 정보 삭제 실패 (ID: ${id}):`, error);
    throw error;
  }
};
