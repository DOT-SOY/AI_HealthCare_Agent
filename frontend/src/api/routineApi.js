import jwtAxios from '../util/jwtUtil';

export const routineApi = {
  getToday: async () => {
    const response = await jwtAxios.get('/routines/today');
    return response.data;
  },
  
  getWeek: async () => {
    const response = await jwtAxios.get('/routines/weekly');
    return response.data;
  },
  
  getHistory: async (bodyPart = null) => {
    const params = bodyPart ? { bodyPart } : {};
    const response = await jwtAxios.get('/routines/history', { params });
    return response.data;
  },

  getLatestByExercise: async () => {
    const response = await jwtAxios.get('/routines/history/latest');
    return response.data;
  },

  getRoutinesByExercise: async (exerciseName, page = 0, size = 1) => {
    const response = await jwtAxios.get(
      `/routines/history/exercise/${encodeURIComponent(exerciseName)}`,
      { params: { page, size } }
    );
    return response.data;
  },

  getById: async (routineId) => {
    const response = await jwtAxios.get(`/routines/${routineId}`);
    return response.data;
  },
  
  getByDate: async (date) => {
    // 날짜 문자열로 변환 (YYYY-MM-DD)
    const dateStr =
      typeof date === 'string'
        ? date
        : new Date(date).toISOString().split('T')[0];

    try {
      const response = await jwtAxios.get('/routines/by-date', {
        params: { date: dateStr },
      });
      return response.data;
    } catch (error) {
      console.error('날짜별 루틴 조회 실패:', error);
      return null;
    }
  },
  
  create: async (date, title, summary) => {
    const response = await jwtAxios.post('/routines', {
      date: date,
      title: title || '새로운 루틴',
      summary: summary || '',
    });
    return response.data;
  },
  
  updateStatus: async (id, status) => {
    const response = await jwtAxios.put(`/routines/${id}/status`, { status });
    return response.data;
  },

  /** AI 루틴 추천 모달에서 "루틴 생성하기" 시 오늘부터 N일치 루틴 저장 */
  createFromRecommendation: async (payload) => {
    const response = await jwtAxios.post('/routines/from-recommendation', payload);
    return response.data;
  },

  /** 메인 화면 볼륨 차트용 총 볼륨 통계 조회
   *  - period: 'month' | 'week'
   */
  getVolumeStats: async (period = 'month') => {
    const response = await jwtAxios.get('/routines/volume-stats', {
      params: { period },
    });
    return response.data;
  },

  /** 통증 수정 모달에서 선택한 대체 운동 적용 */
  applyPainModify: async (payload) => {
    const response = await jwtAxios.post('/routines/pain-modify-apply', payload);
    return response.data;
  },
};

