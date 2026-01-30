import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { toggleChat, addMessage, setLoading, incrementNotification, clearNotification } from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';

export default function AIChatOverlay() {
  const dispatch = useDispatch();
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
    await sendAIMessage(text);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <>
      {/* 플로팅 버튼 */}
      {!isChatOpen && (
        <div className="fixed bottom-8 right-8 z-50">
          <button
            onClick={() => dispatch(toggleChat())}
            className="w-16 h-16 rounded-full flex items-center justify-center shadow-lg transition-colors relative"
            style={{ backgroundColor: '#88ce02' }}
            onMouseEnter={(e) => e.target.style.backgroundColor = 'rgba(136, 206, 2, 0.8)'}
            onMouseLeave={(e) => e.target.style.backgroundColor = '#88ce02'}
          >
            <svg className="w-8 h-8 text-neutral-950" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
            {/* 알림 배지 */}
            {notificationCount > 0 && (
              <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center border-2 border-neutral-950">
                {notificationCount > 9 ? '9+' : notificationCount}
              </span>
            )}
          </button>
        </div>
      )}

      {/* 채팅 패널 */}
      {isChatOpen && (
        <div className="fixed bottom-8 right-8 w-96 h-[600px] bg-neutral-900 rounded-lg shadow-2xl border border-neutral-700 flex flex-col z-50">
          {/* 헤더 */}
          <div className="flex items-center justify-between p-4 border-b border-neutral-700">
            <h2 className="text-lg font-semibold text-neutral-50">AI 코치</h2>
            <button
              onClick={() => dispatch(toggleChat())}
              className="text-neutral-400 hover:text-neutral-50 transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* 메시지 영역 */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {messages.length === 0 && (
              <div className="text-center text-neutral-400 py-8">
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
                  className={`max-w-[80%] rounded-lg px-4 py-2 ${
                    message.role === 'user'
                      ? 'text-white'
                      : 'bg-neutral-800 text-neutral-50'
                  }`}
                  style={message.role === 'user' ? { backgroundColor: 'rgba(136, 206, 2, 0.9)' } : {}}
                >
                  <p className="text-sm whitespace-pre-wrap font-medium drop-shadow-[0_1px_2px_rgba(0,0,0,0.3)]">{message.content}</p>
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex justify-start">
                <div className="bg-neutral-800 rounded-lg px-4 py-2">
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-neutral-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                    <div className="w-2 h-2 bg-neutral-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                    <div className="w-2 h-2 bg-neutral-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* 입력 영역 */}
          <div className="p-4 border-t border-neutral-700">
            <div className="flex gap-2">
              <button
                onClick={isListening ? stopListening : startListening}
                className={`p-2 rounded-lg transition-colors ${
                  isListening
                    ? 'bg-red-500 text-white'
                    : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-700'
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
                className="flex-1 bg-neutral-800 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2"
                style={{ '--tw-ring-color': '#88ce02' }}
                onFocus={(e) => e.target.style.boxShadow = '0 0 0 2px #88ce02'}
                onBlur={(e) => e.target.style.boxShadow = ''}
                disabled={loading}
              />
              <button
                onClick={handleSend}
                disabled={!inputText.trim() || loading}
                className="text-neutral-950 px-4 py-2 rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ backgroundColor: '#88ce02' }}
                onMouseEnter={(e) => {
                  if (!e.currentTarget.disabled) {
                    e.currentTarget.style.backgroundColor = 'rgba(136, 206, 2, 0.8)';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!e.currentTarget.disabled) {
                    e.currentTarget.style.backgroundColor = '#88ce02';
                  }
                }}
              >
                전송
              </button>
            </div>
            {isListening && (
              <p className="text-xs mt-2" style={{ color: '#88ce02' }}>음성 인식 중...</p>
            )}
          </div>
        </div>
      )}
    </>
  );
}

