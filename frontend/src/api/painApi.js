import apiClient from './axiosConfig';

export const painApi = {
  report: async (bodyPart, note) => {
    const response = await apiClient.post('/api/pain/report', {
      bodyPart,
      note,
    });
    return response.data;
  },
};


