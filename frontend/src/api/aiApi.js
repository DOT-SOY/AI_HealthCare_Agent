import jwtAxios from '../services/jwtAxios';

export const aiApi = {
  sendMessage: async (text) => {
    const response = await jwtAxios.post('/ai/chat', { text });
    return response.data;
  },
};


