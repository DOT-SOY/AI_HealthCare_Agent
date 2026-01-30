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
};

