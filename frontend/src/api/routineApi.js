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
    // 주간 루틴에서 해당 날짜 찾기
    const response = await jwtAxios.get(`/routines/weekly`);
    const routines = response.data;
    const targetDate = new Date(date).toISOString().split('T')[0];
    return routines.find(r => r.date === targetDate) || null;
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

  /** 프리셋 루틴 그룹 목록 (카드 1: 분할 4일, 카드 2: 상하체 2일) */
  getPresets: async () => {
    const response = await jwtAxios.get('/routines/presets');
    return response.data;
  },

  /** 선택한 프리셋 적용. startDate부터 연속 일수만큼 루틴 저장. presetIndex 0=4일, 1=2일 */
  applyPreset: async (startDate, presetIndex) => {
    const dateStr = typeof startDate === 'string' ? startDate : startDate.toISOString().split('T')[0];
    const response = await jwtAxios.post('/routines/apply-preset', {
      startDate: dateStr,
      presetIndex,
    });
    return response.data;
  },
};

