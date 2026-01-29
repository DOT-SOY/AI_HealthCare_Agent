import { useDispatch, useSelector } from 'react-redux';
import { addMessage, setLastResponse, setLoading } from '../store/aiSlice';
import { aiApi } from '../api/aiApi';

export function useAI() {
  const dispatch = useDispatch();
  const { messages, lastResponse, loading } = useSelector((state) => state.ai);

  const sendAIMessage = async (text) => {
    try {
      dispatch(setLoading(true));
      
      // 사용자 메시지 추가
      dispatch(addMessage({ role: 'user', content: text }));
      
      // AI API 호출
      const response = await aiApi.sendMessage(text);
      
      // AI 응답 추가 (백엔드 응답 형식에 맞춤)
      let aiResponseText = response.message || response.aiAnswer;
      
      // 응답이 없거나 빈 문자열인 경우 처리
      if (!aiResponseText || aiResponseText.trim() === '') {
        console.error('AI 응답이 비어있습니다:', { response, intent: response.intent });
        if (response.intent === 'GENERAL_CHAT') {
          aiResponseText = '죄송합니다. 응답을 생성하는 중 오류가 발생했습니다. 다시 시도해주세요.';
        } else if (response.intent === 'PAIN_REPORT') {
          aiResponseText = '통증 정보를 처리하는 중 오류가 발생했습니다. 다시 시도해주세요.';
        } else {
          aiResponseText = '응답을 받을 수 없습니다.';
        }
      }
      
      dispatch(addMessage({ role: 'assistant', content: aiResponseText }));
      dispatch(setLastResponse(response));
      
      return response;
    } catch (error) {
      console.error('AI 메시지 전송 실패:', error);
      const errorMessage = error.response?.data?.message || error.message || '죄송합니다. 오류가 발생했습니다.';
      dispatch(addMessage({ 
        role: 'assistant', 
        content: `오류: ${errorMessage}` 
      }));
      throw error;
    } finally {
      dispatch(setLoading(false));
    }
  };

  const clearMessages = () => {
    dispatch({ type: 'ai/clearMessages' });
  };

  return {
    messages,
    lastResponse,
    loading,
    sendAIMessage,
    clearMessages,
  };
}

