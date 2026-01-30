import jwtAxios from "../util/jwtUtil";

const mealBase = "/v1/meal";

export const mealApi = {
    // 대시보드 데이터 조회 (GET)
    getDashboard: async (date) => {
        try {
            const response = await jwtAxios.get(`${mealBase}/dashboard`, { params: { date } });
            return response.data;
        } catch (error) {
            console.error("대시보드 조회 실패:", error);
            throw error;
        }
    },

    // 식사 완료/취소 토글 (PATCH)
    toggleStatus: async (scheduleId, status) => {
        return await jwtAxios.patch(`${mealBase}/intake/${scheduleId}/status`, null, { params: { status } });
    }
};

