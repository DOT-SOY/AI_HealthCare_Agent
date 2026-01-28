import jwtAxios from "../util/jwtUtil";

const API_SERVER_HOST =
  import.meta.env.VITE_API_SERVER_HOST || "http://localhost:8080";

const host = `${API_SERVER_HOST}/api/v1/meal`;

export const mealApi = {
    // 대시보드 데이터 조회 (GET)
    getDashboard: async (date) => {
        try {
            const config = date ? { params: { date } } : {};
            const response = await jwtAxios.get(`${host}/dashboard`, config);
            return response.data;
        } catch (error) {
            console.error("대시보드 조회 실패:", error);
            throw error;
        }
    },

    // 식사 완료/취소 토글 (PATCH)
    toggleStatus: async (scheduleId, status) => {
        return await jwtAxios.patch(`${host}/intake/${scheduleId}/status`, null, { params: { status } });
    }
};