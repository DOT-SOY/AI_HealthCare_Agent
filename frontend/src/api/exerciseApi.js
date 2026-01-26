import apiClient from './axiosConfig';

export const exerciseApi = {
  toggleCompleted: async (routineId, exerciseId) => {
    const response = await apiClient.patch(
      `/api/routines/${routineId}/exercises/${exerciseId}/toggle`
    );
    return response.data;
  },
  
  add: async (routineId, exercise) => {
    const response = await apiClient.post(
      `/api/routines/${routineId}/exercises`,
      exercise
    );
    return response.data;
  },
  
  update: async (routineId, exerciseId, exercise) => {
    const response = await apiClient.put(
      `/api/routines/${routineId}/exercises/${exerciseId}`,
      exercise
    );
    return response.data;
  },
  
  delete: async (routineId, exerciseId) => {
    await apiClient.delete(
      `/api/routines/${routineId}/exercises/${exerciseId}`
    );
  },
};

