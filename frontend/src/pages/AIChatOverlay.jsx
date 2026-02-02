import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import {
  toggleChat,
  addMessage,
  incrementNotification,
  clearNotification,
  setLastResponse,
} from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';
import ExerciseRecognitionModal from '../components/exercise/ExerciseRecognitionModal';

export default function AIChatOverlay() {
  const dispatch = useDispatch();

  const { isChatOpen, messages, loading, notificationCount } = useSelector((state) => state.ai);

  const { sendAIMessage, lastResponse } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const { subscribeToReview, connectWebSocket, disconnect } = useWebSocket();

  // 입력/이미지/모달
  const [inputText, setInputText] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [isDragging, setIsDragging] = useState(false);

  const [isExerciseModalOpen, setIsExerciseModalOpen] = useState(false);
  const [exerciseName, setExerciseName] = useState(null);
  const [exerciseData, setExerciseData] = useState(null);

  // v2 애니메이션 상태
  const [isLeaving, setIsLeaving] = useState(false);
  const [enterDone, setEnterDone] = useState(false);

  // refs
  const messagesEndRef = useRef(null);
  const previousMessagesLengthRef = useRef(messages.length);
  const wasChatOpenRef = useRef(isChatOpen);

  const fileInputRef = useRef(null);

  const lastMessageRef = useRef(null);
  const lastMessageTimeRef = useRef(0);
  const subscriptionInitializedRef = useRef(false);

  const lastSentTranscriptRef = useRef('');

  // 1) WebSocket: 알림/메시지 수신
  useEffect(() => {
    connectWebSocket();

    if (subscriptionInitializedRef.current) return;
    subscriptionInitializedRef.current = true;

    subscribeToReview((data) => {
      const messageContent =
        data.message || '오늘 운동은 어땠나요? 피드백을 주시면 다음 루틴에 반영하겠습니다.';
      const now = Date.now();

      // 1초 내 동일 메시지 중복 방지
      if (lastMessageRef.current === messageContent && now - lastMessageTimeRef.current < 1000) return;

      lastMessageRef.current = messageContent;
      lastMessageTimeRef.current = now;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));

      // 닫혀있으면 배지만 증가 (자동 오픈 X)
      if (!isChatOpen) dispatch(incrementNotification());
    });

    return () => {
      subscriptionInitializedRef.current = false;
      disconnect();
    };
  }, [subscribeToReview, connectWebSocket, disconnect, dispatch, isChatOpen]);

  // 2) STT: transcript -> input 반영
  useEffect(() => {
    if (transcript) setInputText(transcript);
  }, [transcript]);

  // 3) STT 자동 전송: 듣기 종료 후 transcript가 있으면 전송 (중복 방지)
  useEffect(() => {
    if (!isListening && transcript && transcript.trim() && !loading) {
      const text = transcript.trim();
      if (lastSentTranscriptRef.current === text) return;

      const timer = setTimeout(async () => {
        lastSentTranscriptRef.current = text;
        setInputText('');
        await sendAIMessage(text);
      }, 300);

      return () => clearTimeout(timer);
    }
  }, [isListening, transcript, loading, sendAIMessage]);

  // 4) 스크롤: “열려있고 + 메시지가 실제로 늘었을 때”만 스무스 스크롤
  useEffect(() => {
    if (messages.length > previousMessagesLengthRef.current && isChatOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
    previousMessagesLengthRef.current = messages.length;
  }, [messages, isChatOpen]);

  // 5) 채팅 오픈 시: 맨 아래로 이동 + 알림 초기화
  useEffect(() => {
    if (isChatOpen && !wasChatOpenRef.current) {
      setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'auto' }), 100);
    }
    wasChatOpenRef.current = isChatOpen;

    if (isChatOpen && notificationCount > 0) dispatch(clearNotification());
  }, [isChatOpen, notificationCount, dispatch]);

  // 6) v2 enter 애니메이션 트리거
  useEffect(() => {
    if (isChatOpen && !isLeaving) {
      const id = requestAnimationFrame(() => setEnterDone(true));
      return () => cancelAnimationFrame(id);
    }
    if (!isChatOpen && !isLeaving) setEnterDone(false);
  }, [isChatOpen, isLeaving]);

  // 7) v2 leave 애니메이션 후 실제 닫기
  useEffect(() => {
    if (!isLeaving) return;
    const t = setTimeout(() => {
      dispatch(toggleChat());
      setIsLeaving(false);
    }, 280);
    return () => clearTimeout(t);
  }, [isLeaving, dispatch]);

  // 8) lastResponse 기반 운동 모달 오픈 (v3)
  useEffect(() => {
    if (lastResponse?.data?.openExerciseModal === true) {
      setExerciseName(lastResponse.data.exerciseName || null);
      if (lastResponse.data.exercise) setExerciseData(lastResponse.data.exercise);

      setIsExerciseModalOpen(true);

      // 플래그 리셋(재오픈 방지)
      dispatch(
        setLastResponse({
          ...lastResponse,
          data: { ...lastResponse.data, openExerciseModal: false },
        }),
      );
    }
  }, [lastResponse, dispatch]);

  // -------- handlers --------
  const handleOpen = () => {
    if (!isChatOpen) dispatch(toggleChat());
  };

  const handleClose = () => {
    if (isChatOpen) setIsLeaving(true);
  };

  const handleExerciseModalClose = () => {
    setIsExerciseModalOpen(false);
    setExerciseName(null);
    setExerciseData(null);
  };

  const processImageFile = (file) => {
    if (file.size > 5 * 1024 * 1024) return alert('이미지 크기는 5MB 이하여야 합니다.');
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png'];
    if (!allowedTypes.includes(file.type)) return alert('JPEG/PNG 이미지만 가능합니다.');

    setSelectedImage(file);

    const reader = new FileReader();
    reader.onloadend = () => setImagePreview(reader.result);
    reader.readAsDataURL(file);
  };

  const handleImageSelect = (e) => {
    const file = e.target.files?.[0] || e.dataTransfer?.files?.[0];
    if (file) processImageFile(file);
  };

  const handleSend = async () => {
    if (!inputText.trim() && !selectedImage) return;

    const text = inputText.trim();
    const image = selectedImage;

    setInputText('');
    setSelectedImage(null);
    setImagePreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';

    await sendAIMessage(text, image);
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleStartListening = () => {
    // 새 STT 세션 시작 시 중복 방지 키 초기화
    lastSentTranscriptRef.current = '';
    startListening();
  };

  return (
    <>
      {/* 플로팅 버튼 (닫힘 상태) */}
      {!isChatOpen && !isLeaving && (
        <div className="fixed bottom-8 right-8 z-50 ai-fab-wrapper ai-fab-enter" style={{ overflow: 'visible' }}>
          <button
            type="button"
            onClick={handleOpen}
            className="ai-fab-btn w-16 h-16 rounded-full flex items-center justify-center relative focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
            aria-label="AI 코치 채팅 열기"
          >
            <span className="ai-fab-mask" aria-hidden />
            <svg className="w-8 h-8 relative z-[2] text-neutral-950" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>

            {notificationCount > 0 && (
              <span className="absolute -top-1 -right-1 bg-accent-secondary text-white text-xs font-bold rounded-full min-w-[22px] h-[22px] px-2 flex items-center justify-center border-2 border-bg-root z-[3] shadow-sm leading-none">
                {notificationCount > 9 ? '9+' : notificationCount}
              </span>
            )}
          </button>
        </div>
      )}

      {/* 채팅 패널 (열림 or leave 애니메이션 중) */}
      {(isChatOpen || isLeaving) && (
        <>
          {/* 배경 오버레이 */}
          <div
            className="ai-chat-overlay fixed inset-0 z-40 bg-bg-root/60 backdrop-blur-sm"
            data-visible={enterDone && !isLeaving}
            data-leaving={isLeaving}
            onClick={handleClose}
            aria-hidden
          />

          {/* 채팅 패널 */}
          <div
            className="ai-chat-panel card-token fixed bottom-8 right-8 w-[500px] h-[600px] rounded-token shadow-card flex flex-col z-50 overflow-hidden"
            data-visible={enterDone && !isLeaving}
            data-leaving={isLeaving}
            onClick={(e) => e.stopPropagation()}
          >
            {/* 헤더 */}
            <div className="flex items-center justify-between p-4 border-b border-border-default bg-bg-surface">
              <h2 className="text-lg font-semibold text-text-main">AI 코치</h2>
              <button
                onClick={handleClose}
                className="text-text-sub hover:text-text-main transition-colors p-1 rounded-token-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
                aria-label="채팅 닫기"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* 메시지 영역 */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-bg-root">
              {messages.length === 0 && (
                <div className="text-center text-text-sub py-8">
                  <p>안녕하세요! AI 코치입니다.</p>
                  <p className="mt-2 text-sm">운동 질문이나 통증 보고를 해주세요.</p>
                </div>
              )}

              {messages.map((message, index) => (
                <div key={index} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[85%] rounded-token px-4 py-2 ${
                      message.role === 'user'
                        ? 'bg-primary-500 text-bg-root'
                        : 'bg-bg-surface text-text-main shadow-sm border border-border-default'
                    }`}
                  >
                    {/* 이미지 썸네일 */}
                    {message.imageUrl && (
                      <div className="mb-2">
                        <img
                          src={message.imageUrl}
                          alt="첨부 이미지"
                          className="max-w-[200px] max-h-[200px] rounded-token border border-border-default/50 shadow-sm object-cover"
                        />
                      </div>
                    )}

                    {message.content && <p className="text-sm whitespace-pre-wrap font-medium">{message.content}</p>}

                    {/* WORKOUT 인텐트 UI */}
                    {message.intent === 'WORKOUT' && message.data?.exercises?.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border-default/50 space-y-2">
                        {message.data.exercises.map((ex, idx) => (
                          <div key={idx} className="flex items-center justify-between p-2 rounded bg-bg-root/30 text-xs">
                            <span className={ex.completed ? 'text-primary-500' : 'text-text-sub'}>
                              {ex.completed ? '✓' : '○'} <span className={ex.completed ? 'line-through' : ''}>{ex.name}</span>
                            </span>
                            <span className="text-text-sub">
                              {ex.sets}S × {ex.reps}R
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {loading && (
                <div className="flex justify-start">
                  <div className="bg-bg-surface rounded-token px-4 py-2 flex gap-1">
                    <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                    <div className="w-2 h-2 bg-text-sub rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>

            {/* 입력 영역: 이미지 + 드래그&드롭 + STT + v2 느낌 */}
            <div
              className={`p-4 border-t border-border-default bg-bg-surface ${
                isDragging ? 'bg-primary-500/10 border-2 border-dashed border-primary-500' : ''
              }`}
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={(e) => {
                e.preventDefault();
                setIsDragging(false);
                handleImageSelect(e);
              }}
            >
              {imagePreview && (
                <div className="mb-2 relative inline-block">
                  <img src={imagePreview} alt="미리보기" className="max-w-[120px] rounded-token border border-border-default shadow-sm" />
                  <button
                    onClick={() => {
                      setSelectedImage(null);
                      setImagePreview(null);
                    }}
                    className="absolute -top-2 -right-2 bg-accent-secondary text-white rounded-full w-5 h-5 flex items-center justify-center text-xs"
                    type="button"
                    aria-label="첨부 이미지 제거"
                  >
                    ✕
                  </button>
                </div>
              )}

              <div className="flex gap-2 items-center">
                <button
                  type="button"
                  onClick={isListening ? stopListening : handleStartListening}
                  className={`p-2 rounded-token transition-colors ${
                    isListening
                      ? 'bg-accent-secondary text-white'
                      : 'bg-bg-root text-text-sub hover:text-text-main border border-border-default focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50'
                  }`}
                  title="음성 입력"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"
                    />
                  </svg>
                </button>

                <input
                  type="file"
                  accept="image/*"
                  onChange={handleImageSelect}
                  className="hidden"
                  id="image-input"
                  ref={fileInputRef}
                />
                <label
                  htmlFor="image-input"
                  className="p-2 rounded-token bg-bg-root text-text-sub hover:text-text-main border border-border-default cursor-pointer"
                  title="이미지 첨부"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                    />
                  </svg>
                </label>

                <input
                  type="text"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="메시지를 입력하세요..."
                  className="input-token flex-1 px-4 py-2 bg-bg-root disabled:opacity-50"
                  disabled={loading}
                />

                <button
                  type="button"
                  onClick={handleSend}
                  disabled={(!inputText.trim() && !selectedImage) || loading}
                  className="px-4 py-2 rounded-token font-medium transition-all duration-200 bg-primary-500 text-bg-root border border-primary-500 hover:shadow-glow disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-bg-surface disabled:border-border-default disabled:text-text-sub disabled:hover:shadow-none"
                >
                  전송
                </button>
              </div>

              {isListening && (
                <p className="text-[10px] mt-1 text-primary-500 font-medium animate-pulse">음성 인식 활성화 중...</p>
              )}
            </div>
          </div>
        </>
      )}

      {/* 운동 인식 모달 */}
      <ExerciseRecognitionModal
        isOpen={isExerciseModalOpen}
        onClose={handleExerciseModalClose}
        exerciseName={exerciseName}
        exercise={exerciseData}
        onExerciseNotFound={() => dispatch(addMessage({ role: 'assistant', content: '어떤 운동을 시작하시겠어요?' }))}
      />
    </>
  );
}
