import { useState, useEffect, useRef, useCallback } from 'react';
import { Pose } from '@mediapipe/pose';
import { Camera } from '@mediapipe/camera_utils';
import { 
  countRep, 
  generateFeedback,
  validateKeyJoints
} from '../../services/exerciseRecognition';
import { PoseStabilizer } from '../../services/poseStabilizer';
import { exerciseApi } from '../../api/exerciseApi';
import { useExercises } from '../../hooks/useExercises';

export default function ExerciseRecognitionModal({ 
  isOpen, 
  onClose, 
  exerciseName: initialExerciseName = null,
  exercise = null,
  onExerciseNotFound = null
}) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const poseRef = useRef(null);
  const cameraRef = useRef(null);
  
  const [exerciseName, setExerciseName] = useState(initialExerciseName);
  const [currentSet, setCurrentSet] = useState(1);
  const [setReps, setSetReps] = useState([0]); // 각 세트별 횟수
  const [totalReps, setTotalReps] = useState(0);
  const [lastCountTime, setLastCountTime] = useState(null);
  const [repState, setRepState] = useState('down');
  const [currentFeedback, setCurrentFeedback] = useState(null);
  const [feedbackHistory, setFeedbackHistory] = useState([]); // 피드백 히스토리 추적
  const [sessionStartTime, setSessionStartTime] = useState(null);
  const [isCompleted, setIsCompleted] = useState(false);
  const [finalFeedback, setFinalFeedback] = useState(null);
  const [isWaitingCompletion, setIsWaitingCompletion] = useState(false);
  
  const feedbackTimeoutRef = useRef(null);
  const repStateRef = useRef('down');
  const lastCountTimeRef = useRef(null);
  const currentRepFeedbacksRef = useRef([]); // 현재 횟수 구간의 피드백 임시 저장
  const poseStabilizerRef = useRef(null); // 트래킹 안정화 인스턴스
  const consecutiveMissingFramesRef = useRef(0); // 연속 누락 프레임 카운터
  const { toggleCompleted } = useExercises();
  const [ttsEnabled, setTtsEnabled] = useState(true); // TTS 기본값: 활성화

  // 모달이 열릴 때 상태 초기화
  useEffect(() => {
    if (isOpen) {
      if (!initialExerciseName) {
        // 운동명이 없으면 모달을 닫고 채팅창에 메시지 표시
        if (onExerciseNotFound) {
          onExerciseNotFound();
        }
        onClose();
        return;
      }
      setExerciseName(initialExerciseName);
      setCurrentSet(1);
      setSetReps([0]);
      setTotalReps(0);
      setLastCountTime(null);
      lastCountTimeRef.current = null;
      setRepState('down');
      repStateRef.current = 'down';
      setCurrentFeedback(null);
      setFeedbackHistory([]); // 피드백 히스토리 초기화
      currentRepFeedbacksRef.current = []; // 현재 횟수 구간 피드백 초기화
      setSessionStartTime(Date.now());
      setIsCompleted(false);
      setFinalFeedback(null);
      setIsWaitingCompletion(false);
      
      // 트래킹 안정화 인스턴스 초기화
      if (!poseStabilizerRef.current) {
        poseStabilizerRef.current = new PoseStabilizer();
      } else {
        poseStabilizerRef.current.reset();
      }
      consecutiveMissingFramesRef.current = 0;
    }
  }, [isOpen, initialExerciseName, onClose, onExerciseNotFound]);

  // MediaPipe 초기화
  useEffect(() => {
    if (!isOpen) return;

    const pose = new Pose({
      locateFile: (file) => {
        return `https://cdn.jsdelivr.net/npm/@mediapipe/pose/${file}`;
      }
    });

    pose.setOptions({
      modelComplexity: 1,
      smoothLandmarks: true,
      enableSegmentation: false,
      smoothSegmentation: false,
      minDetectionConfidence: 0.6,
      minTrackingConfidence: 0.6
    });

    pose.onResults((results) => {
      if (!videoRef.current || !canvasRef.current) return;

      const canvasCtx = canvasRef.current.getContext('2d');
      canvasCtx.save();
      canvasCtx.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
      canvasCtx.drawImage(results.image, 0, 0, canvasRef.current.width, canvasRef.current.height);

      // 트래킹 안정화: null 프레임 발생 시 마지막 유효 랜드마크 유지
      let stabilizedLandmarks = null;
      if (poseStabilizerRef.current) {
        stabilizedLandmarks = poseStabilizerRef.current.stabilizeLandmarks(results.poseLandmarks);
      } else {
        stabilizedLandmarks = results.poseLandmarks;
      }

      // 랜드마크가 없거나 안정화 실패한 경우
      if (!stabilizedLandmarks) {
        consecutiveMissingFramesRef.current++;
        
        // 30프레임 이상 연속 누락 시 가이드 음성 출력
        if (consecutiveMissingFramesRef.current >= 30 && exerciseName && !isCompleted && ttsEnabled) {
          if ('speechSynthesis' in window) {
            window.speechSynthesis.cancel();
            const utterance = new SpeechSynthesisUtterance('자세를 화면 중앙에 맞춰주세요');
            utterance.lang = 'ko-KR';
            utterance.rate = 1.0;
            utterance.pitch = 1.0;
            utterance.volume = 0.8;
            window.speechSynthesis.speak(utterance);
          }
          // 음성 출력 후 카운터 리셋 (중복 방지)
          consecutiveMissingFramesRef.current = 0;
        }
        
        canvasCtx.restore();
        return;
      }

      // 연속 누락 프레임 카운터 리셋 (유효한 랜드마크가 있으면)
      consecutiveMissingFramesRef.current = 0;

      // 운동별 핵심 관절 검증 (원본 랜드마크로 검증)
      // 필터링은 하지 않고 원본 랜드마크를 사용하되, 핵심 관절만 검증
      if (exerciseName && !validateKeyJoints(stabilizedLandmarks, exerciseName)) {
        // 핵심 관절이 유효하지 않으면 분석 스킵
        // 하지만 랜드마크는 그리기
        if (stabilizedLandmarks) {
          drawConnections(canvasCtx, stabilizedLandmarks, results.poseConnections);
          drawLandmarks(canvasCtx, stabilizedLandmarks);
        }
        canvasCtx.restore();
        return;
      }

      // 랜드마크 그리기 (원본 랜드마크 사용)
      if (stabilizedLandmarks) {
        drawConnections(canvasCtx, stabilizedLandmarks, results.poseConnections);
        drawLandmarks(canvasCtx, stabilizedLandmarks);
      }

      // 운동 진행 중
      if (exerciseName && !isCompleted) {
        // 매 프레임마다 피드백 생성 (화면에는 표시하지 않고 임시 저장)
        // 원본 랜드마크 사용 (필터링 제거)
        const frameFeedback = generateFeedback(stabilizedLandmarks, exerciseName);
        
        // 현재 횟수 구간의 피드백 수집 (부정 피드백만 저장)
        if (frameFeedback) {
          currentRepFeedbacksRef.current.push(frameFeedback);
        }
        
        // 카운팅 (원본 랜드마크 사용 - 필터링 제거)
        const countResult = countRep(stabilizedLandmarks, exerciseName, repStateRef.current);
        if (countResult.count > 0) {
          const now = Date.now();
          
          // 세트 구분 체크: 마지막 카운팅 시간이 있고, 10초 이상 경과했으면 새 세트
          if (lastCountTimeRef.current !== null) {
            const timeSinceLastCount = now - lastCountTimeRef.current;
            if (timeSinceLastCount >= 10000) {
              setCurrentSet(prev => prev + 1);
              setSetReps(prev => [...prev, 0]);
            }
          }
          
          setTotalReps(prev => prev + countResult.count);
          setSetReps(prev => {
            const newReps = [...prev];
            newReps[newReps.length - 1] += countResult.count;
            return newReps;
          });
          lastCountTimeRef.current = now;
          setLastCountTime(now);
          
          // 카운팅 완료 - 해당 구간의 피드백 분석
          const feedbackCounts = {};
          currentRepFeedbacksRef.current.forEach(f => {
            feedbackCounts[f] = (feedbackCounts[f] || 0) + 1;
          });
          
          // 가장 많이 발생한 피드백 선택
          let mainFeedback = null;
          let maxCount = 0;
          Object.entries(feedbackCounts).forEach(([feedback, count]) => {
            if (count > maxCount) {
              maxCount = count;
              mainFeedback = feedback;
            }
          });
          
          // 피드백이 없으면 기본 긍정 메시지 표시
          const displayFeedback = mainFeedback || "좋습니다! 계속하세요.";
          
          // 화면에 표시
          setCurrentFeedback(displayFeedback);
          
          // 히스토리에 저장
          const newRepNumber = totalReps + countResult.count;
          
          // 긍정 피드백 판단: "좋습니다"가 포함된 메시지는 긍정으로 처리
          const isPositiveFeedback = !mainFeedback || 
                                    displayFeedback.includes("좋습니다") || 
                                    displayFeedback.includes("정확합니다") ||
                                    displayFeedback.includes("완전히");
          
          setFeedbackHistory(prev => [...prev, {
            feedback: displayFeedback,
            isPositive: isPositiveFeedback,
            repNumber: newRepNumber
          }]);
          
          // 다음 구간을 위해 초기화
          currentRepFeedbacksRef.current = [];
          
          // 3초 후 피드백 제거
          if (feedbackTimeoutRef.current) {
            clearTimeout(feedbackTimeoutRef.current);
          }
          feedbackTimeoutRef.current = setTimeout(() => {
            setCurrentFeedback(null);
          }, 3000);
        }
        repStateRef.current = countResult.state;
        setRepState(countResult.state);
      }

      canvasCtx.restore();
    });

    poseRef.current = pose;

    // 카메라 초기화
    if (videoRef.current) {
      const camera = new Camera(videoRef.current, {
        onFrame: async () => {
          await pose.send({ image: videoRef.current });
        },
        width: 640,
        height: 480
      });
      camera.start();
      cameraRef.current = camera;
    }

    return () => {
      if (cameraRef.current) {
        cameraRef.current.stop();
      }
      if (feedbackTimeoutRef.current) {
        clearTimeout(feedbackTimeoutRef.current);
      }
      // TTS 정리
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
    };
  }, [isOpen, exerciseName, isCompleted]);

  // 실시간 피드백 TTS 재생
  useEffect(() => {
    if (!currentFeedback || !ttsEnabled || isCompleted) return;
    
    // TTS 재생
    if ('speechSynthesis' in window) {
      // 이전 음성이 있으면 취소
      window.speechSynthesis.cancel();
      
      const utterance = new SpeechSynthesisUtterance(currentFeedback);
      utterance.lang = 'ko-KR';
      utterance.rate = 1.0; // 읽기 속도
      utterance.pitch = 1.0; // 음성 높이
      utterance.volume = 0.8; // 볼륨
      
      utterance.onend = () => {
        // 재생 완료 후 정리
      };
      
      utterance.onerror = (error) => {
        console.error('TTS 재생 오류:', error);
      };
      
      window.speechSynthesis.speak(utterance);
    }
    
    // cleanup: 컴포넌트 언마운트 시 음성 중지
    return () => {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel();
      }
    };
  }, [currentFeedback, ttsEnabled, isCompleted]);

  // 운동 완료 처리
  const handleComplete = async () => {
    setIsCompleted(true);
    if (cameraRef.current) {
      cameraRef.current.stop();
    }

    // 세션 데이터 수집
    const duration = sessionStartTime ? Math.floor((Date.now() - sessionStartTime) / 1000) : 0;
    
    // 실제 데이터 기반 계산
    const totalFeedbackCount = feedbackHistory.length;
    const negativeFeedbackCount = feedbackHistory.filter(f => !f.isPositive).length;
    const positiveFeedbackCount = feedbackHistory.filter(f => f.isPositive).length;
    
    // 실제 자세 오류 비율 계산
    const badPostureRatio = totalFeedbackCount > 0 
      ? Math.round((negativeFeedbackCount / totalFeedbackCount) * 100)
      : 0;
    
    // 가장 많이 발생한 자세 문제 찾기
    const feedbackCounts = {};
    feedbackHistory.forEach(f => {
      if (!f.isPositive) { // 부정 피드백만 카운트
        feedbackCounts[f.feedback] = (feedbackCounts[f.feedback] || 0) + 1;
      }
    });
    
    // 가장 많이 발생한 피드백 찾기
    let mainIssue = '없음';
    let maxCount = 0;
    Object.entries(feedbackCounts).forEach(([feedback, count]) => {
      if (count > maxCount) {
        maxCount = count;
        mainIssue = feedback;
      }
    });
    
    // 부정 피드백이 없으면 "없음"
    if (maxCount === 0) {
      mainIssue = '없음';
    }

    // 최종 피드백 요청
    try {
      const data = await exerciseApi.getSessionFeedback({
        exercise_type: exerciseName,
        total_reps: totalReps,
        duration_sec: duration,
        main_issue: mainIssue,
        bad_posture_ratio: badPostureRatio
      });
      setFinalFeedback(data.feedback);
      setIsWaitingCompletion(true);
    } catch (error) {
      console.error('피드백 요청 실패:', error);
      setFinalFeedback('운동을 완료하셨습니다. 수고하셨습니다!');
      setIsWaitingCompletion(true);
    }
  };

  // 완료 처리 응답 처리
  const handleCompletionResponse = useCallback(async (isPositive) => {
    if (isPositive) {
      // 완료 처리 API 호출
      if (exercise && exercise.routineId && exercise.id) {
        try {
          console.log('운동 완료 처리 시작:', { routineId: exercise.routineId, exerciseId: exercise.id });
          await toggleCompleted(exercise.routineId, exercise.id);
          console.log('운동 완료 처리 성공');
        } catch (error) {
          console.error('운동 완료 처리 실패:', error);
        }
      } else {
        console.warn('운동 완료 처리 실패: exercise 정보가 없습니다.', { exercise });
      }
    }
    onClose();
  }, [exercise, toggleCompleted, onClose]);


  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50"
      onClick={onClose}
    >
      <div 
        className="bg-bg-card rounded-token p-6 w-full max-w-4xl max-h-[90vh] flex flex-col border border-border-default"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-2xl font-bold text-text-main">
            {exerciseName ? exerciseName : '운동명이 지정되지 않았습니다.'}
          </h2>
          <div className="flex items-center gap-3">
            {/* TTS 토글 버튼 */}
            {exerciseName && !isCompleted && (
              <button
                onClick={() => {
                  setTtsEnabled(prev => !prev);
                  // TTS 비활성화 시 현재 재생 중인 음성 중지
                  if (ttsEnabled && 'speechSynthesis' in window) {
                    window.speechSynthesis.cancel();
                  }
                }}
                className={`p-2 rounded-token transition-colors ${
                  ttsEnabled 
                    ? 'bg-primary-500 text-bg-root hover:bg-primary-400' 
                    : 'bg-gray-200 text-text-muted hover:bg-gray-300'
                }`}
                title={ttsEnabled ? '음성 피드백 끄기' : '음성 피드백 켜기'}
              >
                {ttsEnabled ? (
                  <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.383 13H2a1 1 0 01-1-1V8a1 1 0 011-1h2.383l4-4.707a1 1 0 011.617-.793zM14.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.984 0 0013 10a3.983 3.983 0 00-1.172-2.828 1 1 0 010-1.415z" clipRule="evenodd" />
                  </svg>
                ) : (
                  <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.383 13H2a1 1 0 01-1-1V8a1 1 0 011-1h2.383l4-4.707a1 1 0 011.617-.793zM14.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.984 0 0013 10a3.983 3.983 0 00-1.172-2.828 1 1 0 010-1.415z" clipRule="evenodd" />
                    <path d="M3.28 2.22a.75.75 0 00-1.06 1.06l14.5 14.5a.75.75 0 101.06-1.06L3.28 2.22z" />
                  </svg>
                )}
              </button>
            )}
            <button
              onClick={onClose}
              className="text-text-muted hover:text-text-main transition-colors"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>


        {exerciseName && !isCompleted && (
          <div className="flex-1 overflow-y-auto">
            <div className="flex gap-4">
              {/* 비디오 영역 (왼쪽) */}
              <div className="flex-shrink-0">
                <div className="relative">
                  <video
                    ref={videoRef}
                    className="rounded-lg"
                    style={{ transform: 'scaleX(-1)' }}
                    playsInline
                    width={640}
                    height={480}
                  />
                  <canvas
                    ref={canvasRef}
                    className="absolute top-0 left-0 rounded-lg"
                    style={{ transform: 'scaleX(-1)' }}
                    width={640}
                    height={480}
                  />
                </div>
              </div>

              {/* 정보 영역 (오른쪽) */}
              <div className="flex-1 space-y-4 min-w-0">
                <div className="grid grid-cols-2 gap-4">
                  <div className="bg-bg-surface rounded-token p-4 border border-border-default">
                    <p className="text-text-sub text-sm mb-1">총 횟수</p>
                    <p className="text-3xl font-bold text-primary-500">{totalReps}</p>
                  </div>
                  <div className="bg-bg-surface rounded-token p-4 border border-border-default">
                    <p className="text-text-sub text-sm mb-1">현재 세트</p>
                    <p className="text-3xl font-bold text-primary-500">{currentSet}</p>
                  </div>
                </div>

                <div className="bg-bg-surface rounded-token p-4 border border-border-default">
                  <p className="text-text-sub text-sm mb-2">세트별 횟수</p>
                  <div className="flex flex-wrap gap-2">
                    {setReps.map((reps, index) => (
                      <div key={index} className="bg-bg-card rounded-token px-3 py-1 border border-border-default">
                        <span className="text-text-sub text-sm">세트 {index + 1}: </span>
                        <span className="font-bold text-primary-500">{reps}회</span>
                      </div>
                    ))}
                  </div>
                </div>

                {currentFeedback && (
                  <div className="bg-yellow-500/20 dark:bg-yellow-500/20 border border-yellow-500 rounded-token p-3">
                    <p className="text-yellow-600 dark:text-yellow-300 break-words">{currentFeedback}</p>
                  </div>
                )}

                <div>
                  <button
                    onClick={handleComplete}
                    className="w-full py-3 rounded-token font-medium bg-primary-500 text-bg-root transition-colors hover:bg-primary-400"
                  >
                    운동 완료
                  </button>
                </div>
              </div>
            </div>
          </div>    
        )}

        {isCompleted && !finalFeedback && (
          <div className="flex-1 overflow-y-auto flex items-center justify-center">
            <div className="text-center">
              <div className="flex justify-center mb-4">
                <div className="spinner-token" />
              </div>
              <h3 className="text-lg font-semibold text-text-main mb-2">
                운동에 대한 총평을 생성 중입니다...
              </h3>
              <p className="text-text-sub text-sm">
                잠시만 기다려주세요
              </p>
            </div>
          </div>
        )}

        {isCompleted && finalFeedback && (
          <div className="flex-1 overflow-y-auto space-y-4">
            <div className="bg-bg-surface rounded-token p-4 border border-border-default">
              <h3 className="text-lg font-semibold text-text-main mb-2">운동 피드백</h3>
              <p className="text-text-sub whitespace-pre-wrap break-words">{finalFeedback}</p>
            </div>

            {isWaitingCompletion && (
              <div className="space-y-2 pb-4">
                <p className="text-text-main">이 운동을 완료 처리하시겠습니까?</p>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleCompletionResponse(true)}
                    className="flex-1 py-2 rounded-token font-medium bg-primary-500 text-bg-root transition-colors hover:bg-primary-400"
                  >
                    네
                  </button>
                  <button
                    onClick={() => handleCompletionResponse(false)}
                    className="flex-1 py-2 rounded-token font-medium bg-bg-surface text-text-main border border-border-default transition-colors hover:bg-bg-card"
                  >
                    아니오
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// 랜드마크 그리기 헬퍼 함수
function drawConnections(ctx, landmarks, connections) {
  if (!connections) return;
  
  ctx.strokeStyle = '#00FF00';
  ctx.lineWidth = 2;
  
  connections.forEach(([start, end]) => {
    const startPoint = landmarks[start];
    const endPoint = landmarks[end];
    
    if (startPoint && endPoint) {
      ctx.beginPath();
      ctx.moveTo(startPoint.x * ctx.canvas.width, startPoint.y * ctx.canvas.height);
      ctx.lineTo(endPoint.x * ctx.canvas.width, endPoint.y * ctx.canvas.height);
      ctx.stroke();
    }
  });
}

function drawLandmarks(ctx, landmarks) {
  if (!landmarks) return;
  
  ctx.fillStyle = '#FF0000';
  
  landmarks.forEach((landmark) => {
    const x = landmark.x * ctx.canvas.width;
    const y = landmark.y * ctx.canvas.height;
    
    ctx.beginPath();
    ctx.arc(x, y, 3, 0, 2 * Math.PI);
    ctx.fill();
  });
}

