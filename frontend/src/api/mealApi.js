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
  },

  // 월간 식사 캘린더 데이터 조회
  // MealTargetController(/api/v1/meal/target/calendar)와 연동
  getMonthlyCalendar: async (year, month) => {
    // yearMonth는 해당 달의 1일 (예: 2025-02-01)
    const yearMonth = `${year}-${String(month + 1).padStart(2, "0")}-01`;
    try {
      const response = await jwtAxios.get(`${mealBase}/target/calendar`, {
        params: { yearMonth },
      });
      // 백엔드 ApiResponse<T> 형식: { success, message, data }
      const result = response.data?.data || response.data || [];
      return result;
    } catch (error) {
      console.error("월간 식사 캘린더 조회 실패:", error);
      return [];
    }
  },
};

