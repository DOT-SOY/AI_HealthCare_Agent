import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { toggleChat, addMessage, upsertMealGenerateMessage, setLoading, incrementNotification, clearNotification, setLastResponse } from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';
import { mealApi } from '../api/mealApi';
import ExerciseRecognitionModal from '../components/exercise/ExerciseRecognitionModal';

export default function AIChatOverlay() {
  const dispatch = useDispatch();

  // 상태 및 Hook (기능 로직 유지)
  const { isChatOpen, messages, loading, notificationCount } = useSelector((state) => state.ai);
  const { sendAIMessage, lastResponse } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const { subscribeToReview, subscribeToMealGenerate, subscribeToMealVision, subscribeToMealError, subscribeToMealReplan, connectWebSocket, disconnect } = useWebSocket();

  const [inputText, setInputText] = useState('');
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [isExerciseModalOpen, setIsExerciseModalOpen] = useState(false);
  const [exerciseName, setExerciseName] = useState(null);
  const [exerciseData, setExerciseData] = useState(null);
  const [isDragging, setIsDragging] = useState(false);

  const messagesEndRef = useRef(null);
  const previousMessagesLengthRef = useRef(messages.length);
  const wasChatOpenRef = useRef(isChatOpen);
  const fileInputRef = useRef(null);
  const lastMessageRef = useRef(null);
  const lastMessageTimeRef = useRef(0);
  const subscriptionInitializedRef = useRef(false);
  const lastSentTranscriptRef = useRef('');
  const visionPendingRef = useRef(false);

  // 1. WebSocket 및 알림 로직
  useEffect(() => {
    connectWebSocket();
    if (subscriptionInitializedRef.current) return;
    subscriptionInitializedRef.current = true;

    subscribeToReview((data) => {
      const messageContent = data.message || '오늘 운동은 어땠나요? 피드백을 주시면 다음 루틴에 반영하겠습니다.';
      const now = Date.now();
      if (lastMessageRef.current === messageContent && (now - lastMessageTimeRef.current) < 1000) return;

      lastMessageRef.current = messageContent;
      lastMessageTimeRef.current = now;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));
      if (!isChatOpen) dispatch(incrementNotification());
    });

    // 식단 생성 완료 알림 구독
    subscribeToMealGenerate((data) => {
      const messageContent = data.message || '식단 생성이 완료되었습니다.';
      const now = Date.now();
      
      // 같은 메시지가 1초 이내에 다시 오면 무시
      if (lastMessageRef.current === messageContent && (now - lastMessageTimeRef.current) < 1000) {
        return;
      }
      
      lastMessageRef.current = messageContent;
      lastMessageTimeRef.current = now;

      // 식단 생성 진행률은 같은 버블에서 퍼센트 숫자만 업데이트
      if (typeof messageContent === 'string' && messageContent.startsWith('식단 생성')) {
        dispatch(upsertMealGenerateMessage({ content: messageContent }));
      } else {
        dispatch(addMessage({ role: 'assistant', content: messageContent }));
      }
      
      // 채팅창이 닫혀있으면 알림 카운트 증가
      if (!isChatOpen) {
        dispatch(incrementNotification());
      }
    });

    // Replan(재정비) 완료/실패 알림 구독
    subscribeToMealReplan((data) => {
      const messageContent = data.message || '식단 재정비가 완료되었습니다.';
      const now = Date.now();

      if (lastMessageRef.current === messageContent && (now - lastMessageTimeRef.current) < 1000) {
        return;
      }
      lastMessageRef.current = messageContent;
      lastMessageTimeRef.current = now;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));
      dispatch(setLoading(false));

      if (!isChatOpen) {
        dispatch(incrementNotification());
      }
    });

    // Vision 분석 결과 구독 (이미지 업로드 결과)
    subscribeToMealVision((analyzedFood) => {
      visionPendingRef.current = false;
      dispatch(setLoading(false));

      const foodName = analyzedFood?.foodName || '알 수 없음';
      const calories = analyzedFood?.calories ?? 0;
      const carbs = analyzedFood?.carbs ?? 0;
      const protein = analyzedFood?.protein ?? 0;
      const fat = analyzedFood?.fat ?? 0;

      const foodInfo =
        `음식명: ${foodName}\n` +
        `칼로리: ${calories} kcal\n` +
        `탄수화물: ${carbs} g\n` +
        `단백질: ${protein} g\n` +
        `지방: ${fat} g`;

      dispatch(addMessage({
        role: 'assistant',
        content: `이미지 분석 완료!\n\n${foodInfo}\n\n이 메뉴를 오늘 식사에 반영할까요?\n예: "점심으로 바꿔줘", "추가로 기록해줘", "취소"`,
      }));

      setImageFile(null);
      setImagePreview(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }

      if (!isChatOpen) {
        dispatch(incrementNotification());
      }
    });

    // Vision 분석 오류 구독
    subscribeToMealError((data) => {
      visionPendingRef.current = false;
      dispatch(setLoading(false));
      dispatch(addMessage({
        role: 'assistant',
        content: data.message || '이미지 분석 중 오류가 발생했습니다.',
      }));
      if (!isChatOpen) {
        dispatch(incrementNotification());
      }
    });

    return () => {
      subscriptionInitializedRef.current = false;
      disconnect();
    };
  }, [subscribeToReview, subscribeToMealGenerate, subscribeToMealReplan, subscribeToMealVision, subscribeToMealError, connectWebSocket, disconnect, dispatch, isChatOpen]);

  // 2. 음성 인식 및 자동 전송 로직
  useEffect(() => {
    if (transcript) setInputText(transcript);
  }, [transcript]);

  useEffect(() => {
    if (!isListening && transcript && transcript.trim() && !loading) {
      if (lastSentTranscriptRef.current === transcript.trim()) return;
      const timer = setTimeout(async () => {
        const text = transcript.trim();
        lastSentTranscriptRef.current = text;
        setInputText('');
        await sendAIMessage(text);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [isListening, transcript, loading, sendAIMessage]);

  // 3. 스크롤 및 알림 초기화 로직
  useEffect(() => {
    if (messages.length > previousMessagesLengthRef.current && isChatOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
    previousMessagesLengthRef.current = messages.length;
  }, [messages, isChatOpen]);

  useEffect(() => {
    if (isChatOpen && !wasChatOpenRef.current) {
      setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'auto' }), 100);
    }
    wasChatOpenRef.current = isChatOpen;
    if (isChatOpen && notificationCount > 0) dispatch(clearNotification());
  }, [isChatOpen, notificationCount, dispatch]);

  // 4. 운동 모달 제어 로직
  useEffect(() => {
    if (lastResponse?.data?.openExerciseModal === true) {
      setExerciseName(lastResponse.data.exerciseName || null);
      if (lastResponse.data.exercise) setExerciseData(lastResponse.data.exercise);
      setIsExerciseModalOpen(true);
      dispatch(setLastResponse({
        ...lastResponse,
        data: { ...lastResponse.data, openExerciseModal: false }
      }));
    }
  }, [lastResponse, dispatch]);

  // 핸들러 함수들
  const handleExerciseModalClose = () => {
    setIsExerciseModalOpen(false);
    setExerciseName(null);
    setExerciseData(null);
  };

  const isFileDropEvent = (e) => {
    const types = Array.from(e?.dataTransfer?.types || []);
    return types.includes('Files');
  };

  const handleImageInputChange = (e) => {
    const file = e.target.files?.[0] || null;
    // 첨부 버튼 업로드도 업로드 즉시 채팅 버블로 남기고, 입력창 프리뷰는 남기지 않음
    if (file) handleImageFile(file, { showComposerPreview: false });
  };

  const handleImageDrop = (e) => {
    // 채팅 내 이미지(URI/text) 드래그 등은 무시하고, 실제 파일 드롭만 처리
    if (!isFileDropEvent(e)) return;
    const file = e.dataTransfer?.files?.[0] || null;
    if (file) handleImageFile(file, { showComposerPreview: false });
  };

  const handleImageFile = async (file, options = { showComposerPreview: true }) => {
    if (!file || !file.type.startsWith('image/')) {
      return;
    }

    try {
      setImageFile(file);
      if (!options?.showComposerPreview) {
        setImagePreview(null);
      }
      // DataURL 1회만 읽어서 (1)프리뷰 표시 (2)base64 추출에 재사용
      const dataUrl = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const result = reader.result;
          if (typeof result === 'string' && result.includes(',')) {
            resolve(result);
          } else {
            reject(new Error('이미지 읽기 실패'));
          }
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });

      // 첨부 버튼 업로드 루트에서만 입력창 프리뷰를 표시 (Drag&Drop은 채팅 버블만 표시)
      if (options?.showComposerPreview) {
        setImagePreview(dataUrl);
      }
      dispatch(addMessage({
        role: 'user',
        content: '[음식 이미지 업로드]',
        imageUrl: dataUrl,
        meta: { kind: 'MEAL_IMAGE' },
      }));

      // 업로드가 채팅 버블로 반영됐으면, 입력창(첨부) 상태는 즉시 비움
      setImageFile(null);
      setImagePreview(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }

      // 자동으로 이미지 분석 시작
      dispatch(setLoading(true));

      const base64 = dataUrl.split(',')[1] || '';
      // 백엔드는 202 ACCEPTED만 반환하고, 실제 결과는 WebSocket(/topic/meal/vision/{userId})로 옴
      visionPendingRef.current = true;
      await mealApi.analyzeVision(base64);

      dispatch(addMessage({
        role: 'assistant',
        content: '이미지 분석을 시작했어요. 잠시만 기다려주세요...',
      }));
    } catch (error) {
      console.error('이미지 분석 실패:', error);
      visionPendingRef.current = false;
      dispatch(addMessage({ 
        role: 'assistant', 
        content: '이미지 분석 중 오류가 발생했습니다.' 
      }));
    } finally {
      // 결과는 WebSocket으로 오므로 여기서 loading을 끄지 않음 (실패 시에만 위에서 끔)
      if (!visionPendingRef.current) {
        dispatch(setLoading(false));
      }
    }
  };

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
      {/* 플로팅 버튼 (다른 사람의 v2 디자인 적용) */}
      {!isChatOpen && (
        <div className="fixed bottom-8 right-8 z-50 ai-fab-wrapper" style={{ overflow: 'visible' }}>
          <button
            type="button"
            onClick={() => dispatch(toggleChat())}
            className="ai-fab-btn w-16 h-16 rounded-full flex items-center justify-center relative focus:outline-none"
          >
            <span className="ai-fab-mask" aria-hidden />
            <svg className="w-8 h-8 relative z-[2] text-neutral-950" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
          </button>
          {notificationCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-accent-secondary text-white text-xs font-bold rounded-full min-w-[22px] h-[22px] px-2 flex items-center justify-center border-2 border-bg-root z-[3] shadow-sm leading-none">
              {notificationCount > 9 ? '9+' : notificationCount}
            </span>
          )}
        </div>
      )}

      {/* 채팅 패널 */}
      {isChatOpen && (
        <>
          <div className="fixed inset-0 z-40 bg-bg-root/60" onClick={() => dispatch(toggleChat())} aria-hidden />
          <div
            className="card-token fixed bottom-8 right-8 w-[500px] h-[600px] rounded-token shadow-card flex flex-col z-50 overflow-hidden"
            onClick={(e) => e.stopPropagation()}
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
            onDragLeave={(e) => { e.preventDefault(); setIsDragging(false); }}
            onDrop={(e) => { e.preventDefault(); setIsDragging(false); handleImageDrop(e); }}
            style={isDragging ? { borderColor: 'var(--primary-500)', borderWidth: '2px', borderStyle: 'dashed' } : {}}
          >
            {/* 헤더 */}
            <div className="flex items-center justify-between p-4 border-b border-border-default bg-bg-surface">
              <h2 className="text-lg font-semibold text-text-main">AI 코치</h2>
              <button onClick={() => dispatch(toggleChat())} className="text-text-sub hover:text-text-main p-1">
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
                  <p className="mt-2 text-sm">운동이나 식단, 부상관련 질문을 해주세요.</p>
                  <p className="mt-2 text-xs">음식 사진을 드래그 앤 드롭하면 자동으로 분석합니다.</p>
                </div>
              )}
              {messages.map((message, index) => (
                <div key={index} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[85%] rounded-token px-4 py-2 ${
                    message.role === 'user' ? 'bg-primary-500 text-bg-root' : 'bg-bg-surface text-text-main shadow-sm border border-border-default'
                  }`}>
                    {/* 이미지 썸네일 표시 */}
                    {message.imageUrl && (
                      <div className="mb-2">
                        <img 
                          src={message.imageUrl} 
                          alt="첨부 이미지" 
                          draggable={false}
                          onDragStart={(e) => e.preventDefault()}
                          className="max-w-[200px] max-h-[200px] rounded-token border border-border-default/50 shadow-sm object-cover"
                        />
                      </div>
                    )}
                    {message.content && (
                      <p className="text-sm whitespace-pre-wrap font-medium">{message.content}</p>
                    )}

                    {/* WORKOUT 인텐트 UI (기능 v3 유지) */}
                    {message.intent === 'WORKOUT' && message.data?.exercises?.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border-default/50 space-y-2">
                        {message.data.exercises.map((ex, idx) => (
                          <div key={idx} className="flex items-center justify-between p-2 rounded bg-bg-root/30 text-xs">
                            <span className={ex.completed ? 'text-primary-500' : 'text-text-sub'}>
                              {ex.completed ? '✓' : '○'} <span className={ex.completed ? 'line-through' : ''}>{ex.name}</span>
                            </span>
                            <span className="text-text-sub">{ex.sets}S × {ex.reps}R</span>
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

            {/* 입력 영역 (v3의 이미지 기능 + v2의 디자인) */}
            <div className="p-4 border-t border-border-default bg-bg-surface">
              {imagePreview && (
                <div className="mb-2 relative inline-block">
                  <img
                    src={imagePreview}
                    alt="미리보기"
                    draggable={false}
                    onDragStart={(e) => e.preventDefault()}
                    className="max-w-[120px] rounded-token border border-border-default shadow-sm"
                  />
                  <button onClick={() => { setImageFile(null); setImagePreview(null); }} className="absolute -top-2 -right-2 bg-accent-secondary text-white rounded-full w-5 h-5 flex items-center justify-center text-xs">✕</button>
                </div>
              )}

              <div className="flex gap-2 items-center">
                <button
                  type="button"
                  onClick={isListening ? stopListening : () => { lastSentTranscriptRef.current = ''; startListening(); }}
                  className={`p-2 rounded-token transition-colors ${isListening ? 'bg-accent-secondary text-white' : 'bg-bg-root text-text-sub hover:text-text-main border border-border-default'}`}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" /></svg>
                </button>

                <input type="file" accept="image/*" onChange={handleImageInputChange} className="hidden" id="image-input" ref={fileInputRef} />
                <label htmlFor="image-input" className="p-2 rounded-token bg-bg-root text-text-sub hover:text-text-main border border-border-default cursor-pointer">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
                </label>

                <input
                  type="text"
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="메시지를 입력하세요..."
                  className="input-token flex-1 px-4 py-2 bg-bg-root"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={handleSend}
                  disabled={!inputText.trim() || loading}
                  className="px-4 py-2 rounded-token font-medium transition-all bg-primary-500 text-bg-root disabled:opacity-50"
                >
                  전송
                </button>
              </div>
              {isDragging && (
                <p className="text-xs mt-2 text-center text-primary-500 font-medium">이미지를 놓아주세요...</p>
              )}
              {isListening && <p className="text-[10px] mt-1 text-primary-500 font-medium animate-pulse">음성 인식 활성화 중...</p>}
            </div>
          </div>
        </>
      )}

      {/* 운동 인식 모달 (v3 기능) */}
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