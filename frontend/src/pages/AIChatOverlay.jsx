import { useState, useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import {
  toggleChat,
  addMessage,
  upsertMealGenerateMessage,
  setLoading,
  incrementNotification,
  clearNotification,
  setLastResponse,
} from '../store/aiSlice';
import { useAI } from '../hooks/useAI';
import { useSTT } from '../hooks/useSTT';
import { useWebSocket } from '../hooks/useWebSocket';
import RoutineRecommendModal from '../components/ai/RoutineRecommendModal';
import PainModifyModal from '../components/ai/PainModifyModal';
import OcrInbodyVerifyModal from '../components/profile/OcrInbodyVerifyModal';
import { aiApi } from '../api/aiApi';
import { saveVerifiedBodyInfo } from '../services/ocrInbodyApi';
import ExerciseRecognitionModal from '../components/exercise/ExerciseRecognitionModal';

export default function AIChatOverlay() {
  const dispatch = useDispatch();

  const { isChatOpen, messages, loading, notificationCount, lastResponse } = useSelector((state) => state.ai);
  const { sendAIMessage } = useAI();
  const { isListening, transcript, startListening, stopListening } = useSTT();
  const {
    subscribeToReview,
    subscribeToMealGenerate,
    subscribeToMealVision,
    subscribeToMealError,
    subscribeToMealReplan,
    connectWebSocket,
    disconnect,
  } = useWebSocket();

  // input / image
  const [inputText, setInputText] = useState('');
  const [routineModalOpen, setRoutineModalOpen] = useState(false);
  const [routineModalPayload, setRoutineModalPayload] = useState({ splitType: 2, days: [] });
  const [painModifyModalOpen, setPainModifyModalOpen] = useState(false);
  const [painModifyPayload, setPainModifyPayload] = useState({ date: '', painArea: '', routineTitle: '', replacements: [] });
  const [imagePreview, setImagePreview] = useState(null);
  const [isDragging, setIsDragging] = useState(false);

  // modal
  const [isExerciseModalOpen, setIsExerciseModalOpen] = useState(false);
  const [exerciseName, setExerciseName] = useState(null);
  const [exerciseData, setExerciseData] = useState(null);
  const [isInbodyVerifyOpen, setIsInbodyVerifyOpen] = useState(false);
  const [inbodyVerifyData, setInbodyVerifyData] = useState(null);

  // v2 animation states
  const [isLeaving, setIsLeaving] = useState(false);
  const [enterDone, setEnterDone] = useState(false);

  // refs
  const messagesEndRef = useRef(null);
  const lastOpenedModalForIndexRef = useRef(-1);
  const lastOpenedPainModalForIndexRef = useRef(-1);
  /** 새로고침 시 복원된 메시지로는 모달을 열지 않기 위해, 마운트 시점의 메시지 개수 저장 */
  const initialMessageCountRef = useRef(null);

  const previousMessagesLengthRef = useRef(messages.length);
  const wasChatOpenRef = useRef(isChatOpen);
  const fileInputRef = useRef(null);

  const lastMessageRef = useRef(null);
  const lastMessageTimeRef = useRef(0);
  const subscriptionInitializedRef = useRef(false);
  const lastSentTranscriptRef = useRef('');
  const visionPendingRef = useRef(false);

  // ---------- helpers ----------
  const dedupeWithin1s = (content) => {
    const now = Date.now();
    if (lastMessageRef.current === content && now - lastMessageTimeRef.current < 1000) return true;
    lastMessageRef.current = content;
    lastMessageTimeRef.current = now;
    return false;
  };

  const isFileDropEvent = (e) => {
    const types = Array.from(e?.dataTransfer?.types || []);
    return types.includes('Files');
  };

  const handleOpen = () => {
    if (!isChatOpen) dispatch(toggleChat());
  };

  const handleClose = () => {
    if (isChatOpen && !isLeaving) setIsLeaving(true);
  };

  const handleExerciseModalClose = () => {
    setIsExerciseModalOpen(false);
    setExerciseName(null);
    setExerciseData(null);
  };

  const handleSaveVerifiedInbodyFromChat = async (finalData) => {
    try {
      await saveVerifiedBodyInfo(finalData);
      setIsInbodyVerifyOpen(false);
      setInbodyVerifyData(null);
      if (!dedupeWithin1s('인바디 정보가 저장되었습니다.')) {
        dispatch(addMessage({ role: 'assistant', content: '인바디 정보가 저장되었습니다.' }));
      }
    } catch (err) {
      console.error('인바디 저장 실패:', err);
      if (!dedupeWithin1s('저장에 실패했습니다.')) {
        dispatch(addMessage({ role: 'assistant', content: '저장에 실패했습니다. 다시 시도해주세요.' }));
      }
    }
  };

  // ---------- websocket subscriptions ----------

  useEffect(() => {
    // AIChatOverlay가 마운트될 때 WebSocket 연결 시도 (알림을 받기 위해 미리 연결)
    connectWebSocket();

    // 구독은 한 번만 초기화 (중복 구독 방지)
    if (subscriptionInitializedRef.current) return;
    subscriptionInitializedRef.current = true;

    // WebSocket 구독 - 채팅창이 닫혀있어도 알림을 받아서 메시지에 추가
    subscribeToReview((data) => {
      const messageContent =
        data.message || '오늘 운동은 어땠나요? 피드백을 주시면 다음 루틴에 반영하겠습니다.';
      if (dedupeWithin1s(messageContent)) return;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));
      if (!isChatOpen) dispatch(incrementNotification());
    });

    subscribeToMealGenerate?.((data) => {
      const messageContent = data.message || '식단 생성이 완료되었습니다.';
      if (dedupeWithin1s(messageContent)) return;

      // 진행률 메시지는 같은 버블 업데이트
      if (typeof messageContent === 'string' && messageContent.startsWith('식단 생성')) {
        dispatch(upsertMealGenerateMessage({ content: messageContent }));
      } else {
        dispatch(addMessage({ role: 'assistant', content: messageContent }));
      }

      if (!isChatOpen) dispatch(incrementNotification());
    });

    subscribeToMealReplan?.((data) => {
      const messageContent = data.message || '식단 재정비가 완료되었습니다.';
      if (dedupeWithin1s(messageContent)) return;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));
      dispatch(setLoading(false));

      if (!isChatOpen) dispatch(incrementNotification());
    });

    subscribeToMealVision?.((analyzedFood) => {
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

      const content =
        `이미지 분석 완료!\n\n${foodInfo}\n\n` +
        `이 메뉴를 오늘 식사에 반영할까요?\n` +
        `예: "점심으로 바꿔줘", "추가로 기록해줘", "취소"`;

      if (!dedupeWithin1s(content)) {
        dispatch(addMessage({ role: 'assistant', content }));
      }

      setImagePreview(null);
      if (fileInputRef.current) fileInputRef.current.value = '';

      if (!isChatOpen) dispatch(incrementNotification());
    });

    subscribeToMealError?.((data) => {
      visionPendingRef.current = false;
      dispatch(setLoading(false));

      const messageContent = data.message || '이미지 분석 중 오류가 발생했습니다.';
      if (dedupeWithin1s(messageContent)) return;

      dispatch(addMessage({ role: 'assistant', content: messageContent }));
      if (!isChatOpen) dispatch(incrementNotification());
    });

    return () => {
      subscriptionInitializedRef.current = false;
      disconnect();
    };
  }, [
    subscribeToReview,
    subscribeToMealGenerate,
    subscribeToMealVision,
    subscribeToMealError,
    subscribeToMealReplan,
    connectWebSocket,
    disconnect,
    dispatch,
    isChatOpen,
  ]);

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

  useEffect(() => {
    if (!isListening && transcript && transcript.trim() && !loading) {
      const text = transcript.trim();
      if (lastSentTranscriptRef.current === text) return undefined;

      const timer = setTimeout(async () => {
        lastSentTranscriptRef.current = text;
        setInputText('');
        await sendAIMessage(text);
      }, 300);

      return () => clearTimeout(timer);
    }
    return undefined;
  }, [isListening, transcript, loading, sendAIMessage]);

  const handleStartListening = () => {
    lastSentTranscriptRef.current = '';
    startListening();
  };

  // ---------- scroll / notification ----------
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
    if (isChatOpen && notificationCount > 0) dispatch(clearNotification());
    wasChatOpenRef.current = isChatOpen;
  }, [isChatOpen, notificationCount, dispatch]);

  // 마지막 assistant 메시지에 루틴 추천 / 통증 수정 모달 데이터가 있으면 모달 열기 (같은 메시지에 대해 한 번만)
  // 새로고침 시 복원된 메시지로는 열지 않음 — 이 세션에서 새로 도착한 메시지일 때만 모달 오픈
  // ---------- v2 enter/leave animation ----------
  useEffect(() => {
    if (isChatOpen && !isLeaving) {
      const id = requestAnimationFrame(() => setEnterDone(true));
      return () => cancelAnimationFrame(id);
    }
    if (!isChatOpen && !isLeaving) setEnterDone(false);
  }, [isChatOpen, isLeaving]);

  useEffect(() => {
    if (!isLeaving) return;
    const t = setTimeout(() => {
      dispatch(toggleChat());
      setIsLeaving(false);
    }, 280);
    return () => clearTimeout(t);
  }, [isLeaving, dispatch]);

  // ---------- lastResponse (exercise modal) ----------
  useEffect(() => {
    if (messages.length === 0) return;
    if (initialMessageCountRef.current === null) {
      initialMessageCountRef.current = messages.length;
    }
    if (lastResponse?.data?.openExerciseModal === true) {
      setExerciseName(lastResponse.data.exerciseName || null);
      if (lastResponse.data.exercise) setExerciseData(lastResponse.data.exercise);
      setIsExerciseModalOpen(true);

      dispatch(
        setLastResponse({
          ...lastResponse,
          data: { ...lastResponse.data, openExerciseModal: false },
        }),
      );
    }
    const idx = messages.length - 1;
    const last = messages[idx];
    if (last.role !== 'assistant' || !last.data) return;
    const data = last.data;
    const isNewMessageThisSession = messages.length > initialMessageCountRef.current;
    if (!isNewMessageThisSession) return;

    if (data.openRoutineRecommendModal && Array.isArray(data.days) && data.days.length > 0 && lastOpenedModalForIndexRef.current !== idx) {
      lastOpenedModalForIndexRef.current = idx;
      setRoutineModalPayload({
        splitType: data.splitType ?? 2,
        days: data.days,
      });
      setRoutineModalOpen(true);
    }
    if (data.openPainModifyModal && Array.isArray(data.replacements) && lastOpenedPainModalForIndexRef.current !== idx) {
      lastOpenedPainModalForIndexRef.current = idx;
      setPainModifyPayload({
        date: data.date ?? '',
        painArea: data.painArea ?? '',
        routineTitle: data.routineTitle ?? '',
        replacements: data.replacements ?? [],
      });
      setPainModifyModalOpen(true);
    }
  }, [lastResponse, dispatch, messages]);

  // ---------- image handling (unified to vision pipeline) ----------
  const handleImageInputChange = (e) => {
    const file = e.target.files?.[0] || null;
    if (file) handleImageFile(file, { showComposerPreview: false });
  };

  const handleImageDrop = (e) => {
    if (!isFileDropEvent(e)) return;
    const file = e.dataTransfer?.files?.[0] || null;
    if (file) handleImageFile(file, { showComposerPreview: false });
  };

  const handleImageFile = async (file, options = { showComposerPreview: true }) => {
    if (!file || !file.type.startsWith('image/')) return;

    // (선택) v2의 안전장치 유지
    if (file.size > 5 * 1024 * 1024) {
      dispatch(addMessage({ role: 'assistant', content: '이미지 크기는 5MB 이하여야 합니다.' }));
      return;
    }

    try {
      if (!options?.showComposerPreview) setImagePreview(null);

      const dataUrl = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
          const result = reader.result;
          if (typeof result === 'string' && result.includes(',')) resolve(result);
          else reject(new Error('이미지 읽기 실패'));
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });

      if (options?.showComposerPreview) setImagePreview(dataUrl);

      dispatch(
        addMessage({
          role: 'user',
          content: '',
          imageUrl: dataUrl,
          meta: { kind: 'MEAL_IMAGE' },
        }),
      );

      // 입력창 상태 비우기
      setImagePreview(null);
      if (fileInputRef.current) fileInputRef.current.value = '';

      dispatch(setLoading(true));

      // /api/ai/chat 엔드포인트로 이미지 전송 (이미지 분류 후 음식/인바디 라우팅)
      const response = await aiApi.sendMessage(null, file, null);

      if (response?.intent === 'INBODY_ANALYSIS' && response?.data) {
        visionPendingRef.current = false;
        dispatch(setLoading(false));
        setInbodyVerifyData(response.data);
        setIsInbodyVerifyOpen(true);
        const msg = response.message || '인바디 분석이 완료되었습니다. 내용을 확인한 뒤 저장해주세요.';
        if (!dedupeWithin1s(msg)) {
          dispatch(addMessage({ role: 'assistant', content: msg }));
        }
      } else {
        // 식단 등: 백엔드가 MEAL_QUERY로 비동기 분석 시작, 결과는 WebSocket으로 옴
        visionPendingRef.current = true;
        const msg = response?.message || '이미지 분석을 시작했어요. 잠시만 기다려주세요...';
        dispatch(addMessage({ role: 'assistant', content: msg }));
      }
    } catch (err) {
      console.error('이미지 분석 실패:', err);
      visionPendingRef.current = false;
      dispatch(addMessage({ role: 'assistant', content: '이미지 분석 중 오류가 발생했습니다.' }));
    } finally {
      if (!visionPendingRef.current) dispatch(setLoading(false));
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
      <RoutineRecommendModal
        open={routineModalOpen}
        onClose={() => setRoutineModalOpen(false)}
        splitType={routineModalPayload.splitType}
        days={routineModalPayload.days}
      />
      <PainModifyModal
        open={painModifyModalOpen}
        onClose={() => setPainModifyModalOpen(false)}
        date={painModifyPayload.date}
        painArea={painModifyPayload.painArea}
        routineTitle={painModifyPayload.routineTitle}
        replacements={painModifyPayload.replacements}
      />
      <OcrInbodyVerifyModal
        isOpen={isInbodyVerifyOpen}
        onClose={() => {
          setIsInbodyVerifyOpen(false);
          setInbodyVerifyData(null);
        }}
        data={inbodyVerifyData}
        onSave={handleSaveVerifiedInbodyFromChat}
      />
      {/* 플로팅 버튼 */}
      {!isChatOpen && (
        <div className="fixed bottom-8 right-8 z-50">
      {/* FAB (닫힘 상태) */}
      {!isChatOpen && !isLeaving && (
        <div className="fixed bottom-8 right-8 z-50 ai-fab-wrapper ai-fab-enter" style={{ overflow: 'visible' }}>
          <button
            type="button"
            onClick={handleOpen}
            className="ai-fab-btn w-16 h-16 rounded-full flex items-center justify-center relative focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50"
            aria-label="AI 코치 채팅 열기"
            style={{ backgroundColor: '#88ce02' }}
            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'rgba(136, 206, 2, 0.8)'; }}
            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#88ce02'; }}
          >
            <span className="ai-fab-mask" aria-hidden />
            <svg className="w-8 h-8 relative z-[2] text-neutral-950" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden>
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z" />
            </svg>
          </button>

          {notificationCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-accent-secondary text-white text-xs font-bold rounded-full min-w-[22px] h-[22px] px-2 flex items-center justify-center border-2 border-bg-root z-[3] shadow-sm leading-none pointer-events-none">
              {notificationCount > 9 ? '9+' : notificationCount}
            </span>
          )}
        </div>
      )}
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
            onDragOver={(e) => {
              e.preventDefault();
              setIsDragging(true);
            }}
            onDragLeave={(e) => {
              e.preventDefault();
              setIsDragging(false);
            }}
            onDrop={(e) => {
              e.preventDefault();
              setIsDragging(false);
              handleImageDrop(e);
            }}
            style={isDragging ? { borderColor: 'var(--primary-500)', borderWidth: '2px', borderStyle: 'dashed' } : {}}
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

            {/* 메시지 */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-bg-root">
              {messages.length === 0 && (
                <div className="text-center text-text-sub py-8">
                  <p>안녕하세요! AI 코치입니다.</p>
                  <p className="mt-2 text-sm">운동/식단/통증/쇼핑 관련 질문을 해주세요.</p>
                  <p className="mt-2 text-xs">음식 사진을 드래그 앤 드롭하면 자동으로 분석합니다.</p>
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

                    {message.content && <p className="text-sm whitespace-pre-wrap font-medium">{message.content}</p>}

                    {/* WORKOUT UI */}
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

                    {/* 커머스 UI 블록들 (v2 유지) */}
                    {message.intent !== 'GENERAL_CHAT' &&
                      message.data?.error === 'CONDITION_NO_MATCH' &&
                      message.data?.alternativeCandidates?.length > 0 && (
                        <div className="mt-3 pt-3 border-t border-border-default/50">
                          <p className="text-xs text-text-sub mb-2">참고용 추천</p>
                          <ul className="space-y-1.5">
                            {message.data.alternativeCandidates.map((item, idx) => (
                              <li key={idx}>
                                <Link to={`/shop/detail/${item.productId}`} className="text-xs text-primary-500 hover:underline block py-1">
                                  {item.name}
                                </Link>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                    {message.data?.state === 'CONFIRM_PRODUCT' && message.data?.optionCandidates?.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border-default/50">
                        <p className="text-xs text-text-sub mb-2">다른 옵션</p>
                        <ul className="space-y-1.5">
                          {message.data.optionCandidates.map((item, idx) => (
                            <li key={idx} className="text-xs text-text-main py-1">
                              {idx + 1}. {item.variantName || item.name}
                            </li>
                          ))}
                        </ul>
                        <p className="text-[10px] text-text-sub mt-1">원하는 옵션을 말씀해 주시면 해당 옵션으로 진행할게요.</p>
                      </div>
                    )}

                    {message.data?.state === 'CONFIRM_ADDRESS' && message.data?.addressCandidates?.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-border-default/50">
                        <p className="text-xs text-text-sub mb-2">저장된 배송지</p>
                        <ul className="space-y-1.5">
                          {message.data.addressCandidates.map((addr, idx) => (
                            <li key={addr.id ?? idx} className="text-xs text-text-main py-1">
                              {addr.display}
                            </li>
                          ))}
                        </ul>
                        <p className="text-[10px] text-text-sub mt-1">수취인 이름을 말씀하시면 해당 배송지로 진행할게요.</p>
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

            {/* 입력 */}
            <div className="p-4 border-t border-border-default bg-bg-surface">
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
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                  </svg>
                </button>

                <input type="file" accept="image/*" onChange={handleImageInputChange} className="hidden" id="image-input" ref={fileInputRef} />
                <label htmlFor="image-input" className="p-2 rounded-token bg-bg-root text-text-sub hover:text-text-main border border-border-default cursor-pointer" title="이미지 첨부">
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
                  className="input-token flex-1 px-4 py-2 bg-bg-root disabled:opacity-50"
                  disabled={loading}
                />

                <button
                  type="button"
                  onClick={handleSend}
                  disabled={!inputText.trim() || loading}
                  className="px-4 py-2 rounded-token font-medium transition-all duration-200 bg-primary-500 text-bg-root disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  전송
                </button>
              </div>

              {isDragging && <p/>}
              {isListening && <p className="text-[10px] mt-1 text-primary-500 font-medium animate-pulse">음성 인식 활성화 중...</p>}
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