import apiClient from './axiosConfig';

export const memberApi = {
  getCurrent: async () => {
    const response = await apiClient.get('/api/members/current');
    return response.data;
  },
  
  getById: async (memberId) => {
    const response = await apiClient.get(`/api/members/${memberId}`);
    return response.data;
  },
};


