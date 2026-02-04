import jwtAxios from '../util/jwtUtil';

const BASE_URL = '/member-addr-info';

/**
 * [조회] 내 배송지 목록 조회 (JWT 인증, jwtAxios가 토큰 자동 첨부)
 */
export const getMyAddressList = async () => {
  try {
    const res = await jwtAxios.get(`${BASE_URL}/me`);
    return res.data ?? [];
  } catch (error) {
    console.error('내 배송지 목록 조회 실패:', error);
    throw error;
  }
};

/**
 * [조회] 배송지 목록 조회
 */
export const getMemberInfoAddrList = async (memberId) => {
  try {
    const res = await jwtAxios.get(`${BASE_URL}/member/${memberId}`);
    return res.data ?? [];
  } catch (error) {
    console.error('배송지 목록 조회 실패:', error);
    throw error;
  }
};

/**
 * [생성] 배송지 생성
 */
export const createMemberInfoAddr = async (data) => {
  try {
    const res = await jwtAxios.post(BASE_URL, data);
    return res.data;
  } catch (error) {
    console.error('배송지 생성 실패:', error);
    throw error;
  }
};

/**
 * [수정] 배송지 수정
 */
export const updateMemberInfoAddr = async (id, data) => {
  try {
    const res = await jwtAxios.put(`${BASE_URL}/${id}`, data);
    return res.data;
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
    const res = await jwtAxios.put(`${BASE_URL}/${id}/default`);
    return res.data;
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
    const res = await jwtAxios.delete(`${BASE_URL}/${id}`);
    return res.data;
  } catch (error) {
    console.error(`배송지 삭제 실패 (ID: ${id}):`, error);
    throw error;
  }
};
