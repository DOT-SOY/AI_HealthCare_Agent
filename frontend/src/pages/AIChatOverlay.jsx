import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { toggleChat, addMessage, setLoading, incrementNotification, clearNotification } from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';

export default function AIChatOverlay() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { isChatOpen, messages, loading, notificationCount } = useSelector((state) => state.ai);
  const { sendAIMessage } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const { subscribeToReview, connectWebSocket, disconnect } = useWebSocket();
  const [inputText, setInputText] = useState('');
  const messagesEndRef = useRef(null);

  const lastMessageRef = useRef(null);
  const lastMessageTimeRef = useRef(0);
  const subscriptionInitializedRef = useRef(false);
  
  useEffect(() => {
    // AIChatOverlay가 마운트될 때 WebSocket 연결 시도 (알림을 받기 위해 미리 연결)
    connectWebSocket();
    
    // 구독은 한 번만 초기화 (중복 구독 방지)
    if (subscriptionInitializedRef.current) {
      return;
    }
    
    subscriptionInitializedRef.current = true;
    
    // WebSocket 구독 - 채팅창이 닫혀있어도 알림을 받아서 메시지에 추가
    subscribeToReview((data) => {
      // 중복 메시지 방지: 같은 메시지가 짧은 시간 내에 여러 번 오는 경우 필터링
      const messageContent = data.message || '오늘 운동은 어땠나요? 피드백을 주시면 다음 루틴에 반영하겠습니다.';
      const now = Date.now();
      
      // 같은 메시지가 1초 이내에 다시 오면 무시
      if (lastMessageRef.current === messageContent && (now - lastMessageTimeRef.current) < 1000) {
        return; // 중복 메시지 무시
      }
      
      lastMessageRef.current = messageContent;
      lastMessageTimeRef.current = now;
      
      dispatch(addMessage({
        role: 'assistant',
        content: messageContent,
      }));
      
      // 채팅창이 닫혀있으면 알림 카운트 증가 (자동으로 열지 않음)
      if (!isChatOpen) {
        dispatch(incrementNotification());
      }
    });
    
    return () => {
      subscriptionInitializedRef.current = false;
      // 컴포넌트 언마운트 시 WebSocket 정리 (StrictMode에서 중복 구독 방지)
      disconnect();
    };
  }, [subscribeToReview, connectWebSocket, disconnect, dispatch, isChatOpen]);

  useEffect(() => {
    // 음성 인식 결과를 입력 필드에 반영
    if (transcript) {
      setInputText(transcript);
    }
  }, [transcript]);

  useEffect(() => {
    // 메시지가 추가될 때 스크롤
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 채팅창이 열릴 때 맨 아래로 스크롤
  useEffect(() => {
    if (isChatOpen) {
      // 채팅창이 열릴 때 약간의 지연 후 스크롤 (렌더링 완료 후)
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      }, 100);
    }
  }, [isChatOpen]);

  // 채팅창이 열릴 때 알림 카운트 초기화
  useEffect(() => {
    if (isChatOpen && notificationCount > 0) {
      dispatch(clearNotification());
    }
  }, [isChatOpen, notificationCount, dispatch]);

  const handleSend = async () => {
    if (!inputText.trim()) return;

    const text = inputText.trim();
    setInputText('');
    const response = await sendAIMessage(text);
    // OPEN_OCR: 채팅에서 "OCR 자동분석해줘" 등 요청 시 프로필 OCR UI로 이동
    if (response?.intent === 'OPEN_OCR') {
      dispatch(toggleChat());
      navigate('/profile', { state: { openOcr: true } });
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <>
      {/* 플로팅 버튼 — AI 테마 호버 애니메이션 */}
      {!isChatOpen && (
        <div className="fixed bottom-8 right-8 z-50 ai-fab-wrapper">
          <button
            type="button"
            onClick={() => dispatch(toggleChat())}
            className="ai-fab-btn w-16 h-16 rounded-full flex items-center justify-center relative focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
            aria-label="AI 코치 채팅 열기"
          >
            <span className="ai-fab-mask" aria-hidden />
            <svg className="w-8 h-8 relative z-[2]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
            {/* 알림 배지 */}
            {notificationCount > 0 && (
              <span className="absolute -top-1 -right-1 bg-accent-secondary text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center border-2 border-bg-root z-[2]">
                {notificationCount > 9 ? '9+' : notificationCount}
              </span>
            )}
          </button>
        </div>
      )}

      {/* 채팅 패널 */}
      {isChatOpen && (
        <>
          {/* 배경 오버레이 */}
          <div
            className="fixed inset-0 z-40 bg-bg-root/60"
            onClick={() => dispatch(toggleChat())}
            aria-hidden
          />
          {/* 채팅 패널 */}
          <div
            className="card-token fixed bottom-8 right-8 w-96 h-[600px] rounded-token shadow-card flex flex-col z-50"
            onClick={(e) => e.stopPropagation()}
          >
            {/* 헤더 */}
            <div className="flex items-center justify-between p-4 border-b border-border-default">
              <h2 className="text-lg font-semibold text-text-main">AI 코치</h2>
              <button
                onClick={() => dispatch(toggleChat())}
                className="text-text-sub hover:text-text-main transition-colors p-1 rounded-token-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
                aria-label="채팅 닫기"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* 메시지 영역 */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {messages.length === 0 && (
                <div className="text-center text-text-sub py-8">
                  <p>안녕하세요! AI 코치입니다.</p>
                  <p className="mt-2 text-sm">운동 관련 질문이나 통증 보고를 해주세요.</p>
                </div>
              )}
              {messages.map((message, index) => (
                <div
                  key={index}
                  className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-[80%] rounded-token px-4 py-2 ${
                      message.role === 'user'
                        ? 'bg-primary-500 text-bg-root'
                        : 'bg-bg-surface text-text-main'
                    }`}
                  >
                    <p className="text-sm whitespace-pre-wrap font-medium">{message.content}</p>
                  </div>
                </div>
              ))}
              {loading && (
                <div className="flex justify-start">
                  <div className="bg-bg-surface rounded-token px-4 py-2">
                    <div className="flex gap-1">
                      <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                      <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                      <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* 입력 영역 */}
            <div className="p-4 border-t border-border-default">
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={isListening ? stopListening : startListening}
                className={`p-2 rounded-token transition-colors ${
                  isListening
                    ? 'bg-accent-secondary text-white'
                    : 'bg-bg-surface text-text-sub hover:bg-gray-100 hover:text-text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50'
                }`}
                  title="음성 입력"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                  </svg>
                </button>
                <input
                  type="text"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="메시지를 입력하세요..."
                  className="input-token flex-1 px-4 py-2 disabled:opacity-50"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={handleSend}
                  disabled={!inputText.trim() || loading}
                  className="px-4 py-2 rounded-token font-medium transition-all duration-200 bg-primary-500 text-bg-root border border-primary-500 hover:shadow-glow disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-bg-surface disabled:border-border-default disabled:text-text-sub disabled:hover:shadow-none"
                >
                  전송
                </button>
              </div>
              {isListening && (
                <p className="text-xs mt-2 text-primary-500">음성 인식 중...</p>
              )}
            </div>
          </div>
        </>
      )}
    </>
  );
}

