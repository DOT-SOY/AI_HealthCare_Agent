import { useDispatch, useSelector } from 'react-redux';
import { addMessage, setLastResponse, setLoading } from '../store/aiSlice';
import { aiApi } from '../api/aiApi';

export function useAI() {
  const dispatch = useDispatch();
  const { messages, lastResponse, loading } = useSelector((state) => state.ai);

  const sendAIMessage = async (text, imageFile = null) => {
    try {
      dispatch(setLoading(true));
      
      // 이미지가 있으면 base64로 변환하여 메시지에 저장
      let imageUrl = null;
      if (imageFile) {
        imageUrl = await new Promise((resolve) => {
          const reader = new FileReader();
          reader.onloadend = () => resolve(reader.result);
          reader.readAsDataURL(imageFile);
        });
      }
      
      // 사용자 메시지 추가 (이미지 URL도 함께 저장)
      const userMessageContent = text || '';
      dispatch(addMessage({ 
        role: 'user', 
        content: userMessageContent,
        imageUrl: imageUrl 
      }));
      
      // 최근 대화 2개 추출 (AI 1개 + 사용자 1개)
      const recentMessages = getRecentMessages(messages, 2);
      
      // AI API 호출
      const response = await aiApi.sendMessage(text, imageFile, recentMessages);
      
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
      
      // AI 응답 메시지에 data 정보도 함께 저장 (루틴 정보 등)
      dispatch(addMessage({ 
        role: 'assistant', 
        content: aiResponseText,
        data: response.data || null,
        intent: response.intent || null
      }));
      dispatch(setLastResponse(response));

      // 요일 맞바꾸기 등 루틴 변경 시 화면 자동 새로고침 (WebSocket 미수신 시 대비)
      if (response.data?.routineUpdated === true) {
        window.dispatchEvent(new CustomEvent('routine-updated'));
      }

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

  /**
   * 최근 대화 메시지 추출 (AI 1개 + 사용자 1개)
   */
  const getRecentMessages = (messages, count) => {
    if (!messages || messages.length === 0) {
      return [];
    }
    
    const recent = [];
    // 뒤에서부터 순회
    for (let i = messages.length - 1; i >= 0 && recent.length < count; i--) {
      const msg = messages[i];
      // 첫 번째 메시지이거나 이전 메시지와 역할이 다를 때만 추가
      if (recent.length === 0 || recent[0].role !== msg.role) {
        recent.unshift(msg);
      }
    }
    
    return recent;
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

