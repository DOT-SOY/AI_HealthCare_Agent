import jwtAxios from '../services/jwtAxios';

export const exerciseApi = {
  toggleCompleted: async (routineId, exerciseId) => {
    const response = await jwtAxios.patch(
      `/routines/${routineId}/exercises/${exerciseId}/toggle`
    );
    return response.data;
  },
  
  add: async (routineId, exercise) => {
    const response = await jwtAxios.post(
      `/routines/${routineId}/exercises`,
      exercise
    );
    return response.data;
  },
  
  update: async (routineId, exerciseId, exercise) => {
    const response = await jwtAxios.put(
      `/routines/${routineId}/exercises/${exerciseId}`,
      exercise
    );
    return response.data;
  },
  
  delete: async (routineId, exerciseId) => {
    await jwtAxios.delete(
      `/routines/${routineId}/exercises/${exerciseId}`
    );
  },
};

