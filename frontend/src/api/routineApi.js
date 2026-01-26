import apiClient from './axiosConfig';

export const routineApi = {
  getToday: async () => {
    const response = await apiClient.get('/api/routines/today');
    return response.data;
  },
  
  getWeek: async () => {
    const response = await apiClient.get('/api/routines/weekly');
    return response.data;
  },
  
  getHistory: async (bodyPart = null) => {
    const params = bodyPart ? { bodyPart } : {};
    const response = await apiClient.get('/api/routines/history', { params });
    return response.data;
  },
  
  getById: async (routineId) => {
    const response = await apiClient.get(`/api/routines/${routineId}`);
    return response.data;
  },
  
  getByDate: async (date) => {
    // 주간 루틴에서 해당 날짜 찾기
    const response = await apiClient.get(`/api/routines/weekly`);
    const routines = response.data;
    const targetDate = new Date(date).toISOString().split('T')[0];
    return routines.find(r => r.date === targetDate) || null;
  },
  
  create: async (date, title, summary) => {
    const response = await apiClient.post('/api/routines', {
      date: date,
      title: title || '새로운 루틴',
      summary: summary || '',
    });
    return response.data;
  },
  
  updateStatus: async (id, status) => {
    const response = await apiClient.put(`/api/routines/${id}/status`, { status });
    return response.data;
  },
};

