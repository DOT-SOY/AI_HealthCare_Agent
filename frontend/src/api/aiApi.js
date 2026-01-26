import apiClient from './axiosConfig';

export const aiApi = {
  sendMessage: async (text) => {
    const response = await apiClient.post('/api/ai/chat', { text });
    return response.data;
  },
};


