import { useState, useEffect, useRef, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { toggleChat, addMessage, setLoading, incrementNotification, clearNotification } from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';
import { useRoutines } from '../hooks/useRoutines';
import { routineApi } from '../api/routineApi';

// AI 코치 "루틴 짜달라" 시 모달에 표시할 프리셋 (ListPage·백엔드와 동일)
const PRESET_CARDS = [
  {
    groupName: '분할 루틴',
    days: [
      { title: 'Push Day', summary: '가슴, 어깨, 삼두근을 사용하는 날입니다.', exerciseNames: ['벤치프레스', '오버헤드프레스'] },
      { title: 'Pull Day', summary: '등, 이두근, 후면 사슬을 사용하는 날입니다.', exerciseNames: ['데드리프트', '바벨 컬'] },
      { title: 'Leg Day', summary: '허벅지 앞/뒤, 엉덩이, 종아리를 사용하는 날입니다.', exerciseNames: ['스쿼트', '힙쓰러스트', '카프레이즈'] },
      { title: 'Core Day', summary: '복부와 허리, 몸의 중심을 지탱하는 코어 근육을 사용하는 날입니다.', exerciseNames: ['플랭크', '행잉레그레이즈'] },
    ],
  },
  {
    groupName: '상하체 루틴',
    days: [
      { title: 'Upper Day', summary: '가슴, 어깨, 팔, 그리고 복근을 단련합니다.', exerciseNames: ['벤치프레스', '오버헤드프레스', '바벨 컬', '행잉레그레이즈', '플랭크'] },
      { title: 'Leg Day', summary: '허벅지, 엉덩이, 종아리, 등 하부(후면 사슬)를 단련합니다.', exerciseNames: ['스쿼트', '데드리프트', '힙쓰러스트', '카프레이즈'] },
    ],
  },
];

export default function AIChatOverlay() {
  const dispatch = useDispatch();
  const { isChatOpen, messages, loading, notificationCount } = useSelector((state) => state.ai);
  const { sendAIMessage } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const { subscribeToReview, connectWebSocket, disconnect } = useWebSocket();
  const { fetchTodayRoutine, fetchWeekRoutines } = useRoutines();
  const [inputText, setInputText] = useState('');
  const [showPresetModal, setShowPresetModal] = useState(false);
  const [applyLoading, setApplyLoading] = useState(false);
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
    if (response?.showPresetModal === true) {
      setShowPresetModal(true);
    }
  };

  const handleApplyPreset = useCallback(async (presetIndex) => {
    setApplyLoading(true);
    try {
      const todayStr = new Date().toISOString().split('T')[0];
      await routineApi.applyPreset(todayStr, presetIndex);
      setShowPresetModal(false);
      dispatch(addMessage({ role: 'assistant', content: '루틴을 생성했어요. 루틴 페이지에서 확인해 주세요.' }));
      await fetchTodayRoutine();
      await fetchWeekRoutines();
    } catch (err) {
      console.error('프리셋 적용 실패:', err);
      dispatch(addMessage({ role: 'assistant', content: '루틴 생성에 실패했어요. 다시 시도해 주세요.' }));
    } finally {
      setApplyLoading(false);
    }
  }, [dispatch, fetchTodayRoutine, fetchWeekRoutines]);

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
        <>
          {/* 배경 오버레이 */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => dispatch(toggleChat())}
          />
          {/* 채팅 패널 */}
          <div
            className="fixed bottom-8 right-8 w-96 h-[600px] bg-neutral-900 rounded-lg shadow-2xl border border-neutral-700 flex flex-col z-50"
            onClick={(e) => e.stopPropagation()}
          >
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
        </>
      )}

      {/* 프리셋 선택 모달 (AI 코치 "루틴 짜달라" 시) */}
      {showPresetModal && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 p-4"
          onClick={() => !applyLoading && setShowPresetModal(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="preset-modal-title"
        >
          <div
            className="bg-neutral-800 rounded-xl border border-neutral-600 shadow-xl w-full max-w-md overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-4 border-b border-neutral-600 flex items-center justify-between">
              <h2 id="preset-modal-title" className="text-lg font-bold text-neutral-50">
                  루틴 선택
              </h2>
              <button
                type="button"
                onClick={() => !applyLoading && setShowPresetModal(false)}
                className="text-neutral-400 hover:text-neutral-50 p-1 rounded"
                aria-label="닫기"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="p-4">
              <p className="text-neutral-400 text-sm mb-4">
                오늘부터 연속된 날짜에 루틴이 생성됩니다.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {PRESET_CARDS.map((preset, index) => (
                  <button
                    key={preset.groupName}
                    type="button"
                    disabled={applyLoading}
                    onClick={() => handleApplyPreset(index)}
                    className="text-left p-4 rounded-lg border-2 border-neutral-600 bg-neutral-700/50 hover:border-[#88ce02] hover:bg-neutral-700 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    <div className="font-semibold text-[#88ce02] mb-2">{preset.groupName}</div>
                    <div className="text-neutral-400 text-xs space-y-2">
                      {preset.days?.map((day, i) => (
                        <div key={i} className="border-l-2 border-neutral-600 pl-1.5">
                          <div className="font-medium text-neutral-300">{i + 1}. {day.title}</div>
                          <div className="mt-0.5 text-neutral-500">{day.exerciseNames?.join(', ')}</div>
                        </div>
                      ))}
                    </div>
                    {applyLoading && <div className="mt-2 text-neutral-500 text-xs">적용 중...</div>}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

