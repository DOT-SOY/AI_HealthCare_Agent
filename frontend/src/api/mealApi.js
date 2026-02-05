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

  // 식사 생략 (SKIPPED 처리)
  skipMeal: async (scheduleId) => {
    return await jwtAxios.patch(`${mealBase}/intake/${scheduleId}/status`, null, { params: { status: "SKIPPED" } });
  },

  // [끼니 전체 생략 토글] (PLANNED ↔ SKIPPED)
  toggleMealTimeSkip: async (date, mealTime, alsoReplan = false) => {
    const response = await jwtAxios.post(`${mealBase}/intake/meal-time/skip-toggle`, null, {
      params: { date, mealTime, alsoReplan },
    });
    return response.data;
  },

  // 식단 재정비 (Replan)
  requestReplan: async (date) => {
    const response = await jwtAxios.post(`${mealBase}/ai/replan`, null, { params: { date } });
    return response.data;
  },

  // [AI API] 이미지 분석 요청
  analyzeVision: async (imageBase64) => {
    const response = await jwtAxios.post(`${mealBase}/vision/analyze`, { image: imageBase64 });
    return response.data;
  },

  // [AI API] 이미지 분석 후속 자연어(추가/변경/취소) 의도 판별
  analyzeVisionFollowup: async (userText, analyzedFood) => {
    const response = await jwtAxios.post(`${mealBase}/vision/followup`, { userText, analyzedFood });
    return response.data;
  },

  // [AI] 전역 채팅 "초기화" 시 식단 도메인 컨텍스트(히스토리+pending) 리셋
  resetAiContext: async () => {
    const response = await jwtAxios.post(`${mealBase}/ai/context/reset`);
    return response.data;
  },

  // [AI API] 식단 생성 요청
  generateMealPlan: async (profile, goal, period = "day") => {
    const requestType = period === "week" ? "GENERATE_WEEK" : period === "month" ? "GENERATE_MONTH" : "GENERATE";
    const response = await jwtAxios.post(`${mealBase}/ai/generate`, {
      requestType,
      profile,
      goal,
    });
    return response.data;
  },

  // [Intake] 계획 외 추가 섭취 기록(추가)
  recordIntake: async (mealDto) => {
    const response = await jwtAxios.post(`${mealBase}/intake`, mealDto);
    return response.data;
  },

  // [Intake] 기존 끼니 변경(대체)
  updateIntake: async (scheduleId, mealDto) => {
    const response = await jwtAxios.put(`${mealBase}/intake/${scheduleId}`, mealDto);
    return response.data;
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


