import { useState, useEffect, useRef, useCallback } from 'react';
import { Pose } from '@mediapipe/pose';
import { Camera } from '@mediapipe/camera_utils';
import { 
  countRep, 
  generateFeedback
} from '../../services/exerciseRecognition';
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
  const { toggleCompleted } = useExercises();

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
      minDetectionConfidence: 0.5,
      minTrackingConfidence: 0.5
    });

    pose.onResults((results) => {
      if (!videoRef.current || !canvasRef.current) return;

      const canvasCtx = canvasRef.current.getContext('2d');
      canvasCtx.save();
      canvasCtx.clearRect(0, 0, canvasRef.current.width, canvasRef.current.height);
      canvasCtx.drawImage(results.image, 0, 0, canvasRef.current.width, canvasRef.current.height);

      if (results.poseLandmarks) {
        // 운동 진행 중
        if (exerciseName && !isCompleted) {
          // 매 프레임마다 피드백 생성 (화면에는 표시하지 않고 임시 저장)
          const frameFeedback = generateFeedback(results.poseLandmarks, exerciseName);
          
          // 현재 횟수 구간의 피드백 수집 (부정 피드백만 저장)
          if (frameFeedback) {
            currentRepFeedbacksRef.current.push(frameFeedback);
          }
          
          // 카운팅
          const countResult = countRep(results.poseLandmarks, exerciseName, repStateRef.current);
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

        // 랜드마크 그리기
        drawConnections(canvasCtx, results.poseLandmarks, results.poseConnections);
        drawLandmarks(canvasCtx, results.poseLandmarks);
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
    };
  }, [isOpen, exerciseName, isCompleted]);

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
          <button
            onClick={onClose}
            className="text-text-muted hover:text-text-main transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
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
                    className="w-full py-3 rounded-token font-medium bg-primary-500 text-text-inverse transition-colors hover:bg-primary-400"
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
                    className="flex-1 py-2 rounded-token font-medium bg-primary-500 text-text-inverse transition-colors hover:bg-primary-400"
                  >
                    네
                  </button>
                  <button
                    onClick={() => handleCompletionResponse(false)}
                    className="flex-1 py-2 rounded-token font-medium bg-bg-surface text-text-sub border border-border-default transition-colors hover:bg-bg-card"
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

