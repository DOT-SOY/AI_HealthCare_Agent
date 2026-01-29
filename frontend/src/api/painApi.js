import jwtAxios from '../services/jwtAxios';

export const painApi = {
  report: async (bodyPart, note) => {
    const response = await jwtAxios.post('/pain/report', {
      bodyPart,
      note,
    });
    return response.data;
  },
};


