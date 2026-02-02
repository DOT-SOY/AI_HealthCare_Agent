import jwtAxios from '../util/jwtUtil';

export const aiApi = {
  sendMessage: async (text, imageFile = null, conversationHistory = null) => {
    const formData = new FormData();
    
    if (text) {
      formData.append('text', text);
    }
    
    if (imageFile) {
      formData.append('image', imageFile);
    }
    
    if (conversationHistory && conversationHistory.length > 0) {
      formData.append('conversationHistory', JSON.stringify(conversationHistory));
    }
    
    const response = await jwtAxios.post('/ai/chat', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    
    return response.data;
  },
};


