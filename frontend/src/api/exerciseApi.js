import jwtAxios from '../util/jwtUtil';

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
    const rId = Number(routineId);
    const eId = Number(exerciseId);
    if (Number.isNaN(rId) || Number.isNaN(eId)) {
      throw new Error('루틴 ID 또는 운동 ID가 올바르지 않습니다.');
    }
    await jwtAxios.delete(
      `/routines/${rId}/exercises/${eId}`
    );
  },
  
  getSessionFeedback: async (sessionData) => {
    const response = await jwtAxios.post(
      '/exercise/session/feedback',
      sessionData
    );
    return response.data;
  },
};

