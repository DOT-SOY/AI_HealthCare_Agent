import jwtAxios from "../util/jwtUtil.jsx";

const rankingBase = "/v1/ranking";

export const rankingApi = {
  /**
   * 랭킹 조회
   * - 서버에서 현재 회원과 같은 성별/나이대/운동 목적 기준으로 그룹을 고정해서 계산
   */
  getRanking: async ({ limit = 10 } = {}) => {
    const params = { limit };
    const response = await jwtAxios.get(rankingBase, { params });
    return response.data;
  },
};


