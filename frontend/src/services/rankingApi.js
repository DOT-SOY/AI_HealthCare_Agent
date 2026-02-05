import fetchAPI from "./api.js";

/**
 * 랭킹 조회 (운동 목적별 루틴+식단 수행률 순위)
 * @param {number} period - 기준일로부터 며칠 전까지 (기본 30)
 * @returns {Promise<{ success, message, data }>}
 */
export const getRanking = async (period = 30) => {
  const params = new URLSearchParams({ period: String(period) });
  const response = await fetchAPI(`/ranking?${params}`);
  return response;
};
