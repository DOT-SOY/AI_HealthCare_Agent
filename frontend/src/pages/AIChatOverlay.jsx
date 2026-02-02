import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { toggleChat, addMessage, setLoading, incrementNotification, clearNotification, setLastResponse } from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';
import ExerciseRecognitionModal from '../components/exercise/ExerciseRecognitionModal';

export default function AIChatOverlay() {
  const dispatch = useDispatch();
  const { isChatOpen, messages, loading, notificationCount } = useSelector((state) => state.ai);
  const { todayRoutine } = useSelector((state) => state.routines);
  const { sendAIMessage, lastResponse } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const { subscribeToReview, connectWebSocket, disconnect } = useWebSocket();
  const [inputText, setInputText] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [isExerciseModalOpen, setIsExerciseModalOpen] = useState(false);
  const [exerciseName, setExerciseName] = useState(null);
  const [exerciseData, setExerciseData] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const exerciseModalOpenedRef = useRef(false);
  const messagesEndRef = useRef(null);
  const previousMessagesLengthRef = useRef(messages.length);
  const wasChatOpenRef = useRef(isChatOpen);
  const fileInputRef = useRef(null);

  const lastMessageRef = useRef(null);
  const lastMessageTimeRef = useRef(0);
  const subscriptionInitializedRef = useRef(false);
  const lastSentTranscriptRef = useRef(''); // 마지막으로 전송한 transcript 추적
  
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

  // 음성 인식이 끝났을 때 자동으로 전송
  useEffect(() => {
    // 음성 인식이 끝나고 (isListening이 false), transcript가 있고, loading이 아닐 때
    if (!isListening && transcript && transcript.trim() && !loading) {
      // 중복 전송 방지: 이전에 전송한 transcript와 같으면 무시
      if (lastSentTranscriptRef.current === transcript.trim()) {
        return;
      }

      // 약간의 지연을 두어 최종 결과가 확정된 후 전송
      const timer = setTimeout(async () => {
        const text = transcript.trim();
        lastSentTranscriptRef.current = text; // 전송한 transcript 저장
        setInputText(''); // 입력 필드 초기화
        await sendAIMessage(text);
      }, 300); // 300ms 지연으로 최종 결과 확정 대기
      
      return () => clearTimeout(timer);
    }
  }, [isListening, transcript, loading, sendAIMessage]);

  // 메시지가 실제로 추가될 때만 스크롤
  useEffect(() => {
    // 실제로 메시지가 추가되었을 때만 스크롤 (페이지 이동 시 재렌더링 방지)
    if (messages.length > previousMessagesLengthRef.current && isChatOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
    previousMessagesLengthRef.current = messages.length;
  }, [messages, isChatOpen]);

  // 채팅창이 닫혔다가 다시 열릴 때만 스크롤
  useEffect(() => {
    // 이전에 닫혀있었다가 지금 열렸을 때만 스크롤 (페이지 이동 시 재렌더링 방지)
    if (isChatOpen && !wasChatOpenRef.current) {
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      }, 100);
    }
    wasChatOpenRef.current = isChatOpen;
  }, [isChatOpen]);

  // 채팅창이 열릴 때 알림 카운트 초기화
  useEffect(() => {
    if (isChatOpen && notificationCount > 0) {
      dispatch(clearNotification());
    }
  }, [isChatOpen, notificationCount, dispatch]);


  // 운동 모달 열기 처리 (백엔드에서 openExerciseModal을 보낼 때만 열기)
  useEffect(() => {
    if (lastResponse && lastResponse.data && lastResponse.data.openExerciseModal === true) {
      console.log('운동 모달 열기:', { exerciseName: lastResponse.data.exerciseName });
      setExerciseName(lastResponse.data.exerciseName || null);
      // 백엔드에서 전달한 exercise 정보 저장 (routineId 포함)
      if (lastResponse.data.exercise) {
        setExerciseData(lastResponse.data.exercise);
      }
      setIsExerciseModalOpen(true);
      
      // 모달을 열었으므로 openExerciseModal 플래그 제거 (다시 열리지 않도록)
      dispatch(setLastResponse({
        ...lastResponse,
        data: {
          ...lastResponse.data,
          openExerciseModal: false
        }
      }));
    }
  }, [lastResponse, dispatch]);

  // 모달이 닫힐 때 플래그 리셋
  const handleExerciseModalClose = () => {
    setIsExerciseModalOpen(false);
    exerciseModalOpenedRef.current = false;
    setExerciseName(null);
    setExerciseData(null);
    
    // 모달을 닫을 때도 openExerciseModal 플래그 제거
    if (lastResponse && lastResponse.data) {
      dispatch(setLastResponse({
        ...lastResponse,
        data: {
          ...lastResponse.data,
          openExerciseModal: false
        }
      }));
    }
  };

  // 운동을 찾지 못했을 때 채팅창에 메시지 표시
  const handleExerciseNotFound = () => {
    dispatch(addMessage({
      role: 'assistant',
      content: '운동명이 지정되지 않았습니다. 어떤 운동을 시작하시겠어요?'
    }));
  };

  const handleImageSelect = (e) => {
    const file = e.target.files?.[0] || e.dataTransfer?.files?.[0];
    if (file) {
      processImageFile(file);
    }
  };

  const processImageFile = (file) => {
    // 파일 크기 검증 (5MB)
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      alert('이미지 크기는 5MB 이하여야 합니다.');
      return;
    }
    
    // 파일 타입 검증 (JPEG, PNG만)
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png'];
    if (!allowedTypes.includes(file.type)) {
      alert('JPEG 또는 PNG 형식의 이미지만 업로드할 수 있습니다.');
      return;
    }
    
    setSelectedImage(file);
    
    // 미리보기 생성
    const reader = new FileReader();
    reader.onloadend = () => {
      setImagePreview(reader.result);
    };
    reader.readAsDataURL(file);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    
    const file = e.dataTransfer.files[0];
    if (file) {
      processImageFile(file);
    }
  };

  const handleRemoveImage = () => {
    setSelectedImage(null);
    setImagePreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSend = async () => {
    if (!inputText.trim() && !selectedImage) return;

    const text = inputText.trim();
    const image = selectedImage;
    
    setInputText('');
    setSelectedImage(null);
    setImagePreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    
    await sendAIMessage(text, image);
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
        <>
          {/* 배경 오버레이 */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => dispatch(toggleChat())}
          />
          {/* 채팅 패널 */}
          <div
            className="fixed bottom-8 right-8 w-[500px] h-[600px] bg-neutral-900 rounded-lg shadow-2xl border border-neutral-700 flex flex-col z-50"
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
                  
                  {/* WORKOUT intent이고 routine 데이터가 있으면 운동 목록 표시 */}
                  {message.intent === 'WORKOUT' && message.data && message.data.exercises && Array.isArray(message.data.exercises) && message.data.exercises.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-neutral-700">
                      <div className="space-y-2">
                        {message.data.exercises.map((exercise, exIndex) => (
                          <div 
                            key={exercise.id || exIndex}
                            className={`flex items-center justify-between p-2 rounded ${
                              exercise.completed 
                                ? 'bg-neutral-700/50' 
                                : 'bg-neutral-700/30'
                            }`}
                          >
                            <div className="flex items-center gap-2">
                              <span className={exercise.completed ? 'text-[#88ce02]' : 'text-neutral-400'}>
                                {exercise.completed ? '✓' : '○'}
                              </span>
                              <span className={`text-sm ${exercise.completed ? 'line-through text-neutral-400' : 'text-neutral-200'}`}>
                                {exercise.name || '알 수 없는 운동'}
                              </span>
                            </div>
                            {exercise.sets && exercise.reps && (
                              <span className="text-xs text-neutral-400">
                                {exercise.sets}세트 × {exercise.reps}회
                                {exercise.weight && exercise.weight > 0 && ` (${exercise.weight}kg)`}
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
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
          <div 
            className={`px-4 py-3 border-t border-neutral-700 ${isDragging ? 'bg-neutral-800/50 border-2 border-dashed border-[#88ce02]' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            {/* 이미지 미리보기 */}
            {imagePreview && (
              <div className="mb-2 relative inline-block">
                <img 
                  src={imagePreview} 
                  alt="미리보기" 
                  className="max-w-[200px] max-h-[200px] rounded-lg object-cover"
                />
                <button
                  onClick={handleRemoveImage}
                  className="absolute top-1 right-1 bg-red-500 text-white rounded-full w-6 h-6 flex items-center justify-center hover:bg-red-600"
                  title="이미지 제거"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            )}
            
            <div className="flex gap-2 items-center">
              <button
                onClick={() => {
                  if (isListening) {
                    stopListening();
                  } else {
                    lastSentTranscriptRef.current = ''; // 음성 인식 시작 시 이전 전송 기록 초기화
                    startListening();
                  }
                }}
                className={`p-2 rounded-lg transition-colors flex-shrink-0 ${
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
              
              {/* 이미지 첨부 버튼 */}
              <input
                type="file"
                accept="image/jpeg,image/jpg,image/png"
                onChange={handleImageSelect}
                className="hidden"
                id="image-input"
                ref={fileInputRef}
              />
              <label
                htmlFor="image-input"
                className="p-2 rounded-lg bg-neutral-800 text-neutral-400 hover:bg-neutral-700 cursor-pointer flex-shrink-0"
                title="이미지 첨부"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </label>
              
              <input
                type="text"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="메시지를 입력하세요..."
                className="flex-1 bg-neutral-800 text-neutral-50 px-4 py-2 rounded-lg focus:outline-none focus:ring-2 h-10"
                style={{ '--tw-ring-color': '#88ce02' }}
                onFocus={(e) => e.target.style.boxShadow = '0 0 0 2px #88ce02'}
                onBlur={(e) => e.target.style.boxShadow = ''}
                disabled={loading}
              />
              <button
                onClick={handleSend}
                disabled={(!inputText.trim() && !selectedImage) || loading}
                className="text-neutral-950 px-4 py-2 rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed h-10 flex-shrink-0"
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

      {/* 운동 인식 모달 */}
      <ExerciseRecognitionModal
        isOpen={isExerciseModalOpen}
        onClose={handleExerciseModalClose}
        exerciseName={exerciseName}
        exercise={exerciseData}
        onExerciseNotFound={handleExerciseNotFound}
      />
    </>
  );
}

