import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ExerciseRecognitionModal from '../exercise/ExerciseRecognitionModal';

export default function TodaysFocus({ routine, selectedExercise }) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();

  const handleStartSession = () => {
    if (!selectedExercise) {
      // 선택된 운동이 없으면 루틴 페이지로 이동
      navigate('/routine/list');
      return;
    }

    // 선택된 운동의 분석 모달 열기
    setIsModalOpen(true);
  };

  const handleModalClose = () => {
    setIsModalOpen(false);
  };

  return (
    <div className="w-full">
      <h2 className="text-lg font-bold text-text-main mb-4">TODAY'S FOCUS</h2>
      
      {routine ? (
        <div className="space-y-3">
          <div>
            <h3 className="text-xl font-bold text-text-main mb-2">
              {routine.title || '오늘의 루틴'}
            </h3>
            <p className="text-text-sub text-sm leading-relaxed">
              {routine.summary || routine.aiSummary || 'AI 코칭 요약이 없습니다'}
            </p>
          </div>

          <button
            onClick={handleStartSession}
            disabled={!selectedExercise}
            className={`w-full py-3 px-5 rounded-token text-base font-medium transition-colors flex items-center justify-center gap-2 ${
              selectedExercise
                ? 'bg-primary-500 text-bg-root hover:opacity-90'
                : 'bg-gray-500 text-gray-300 cursor-not-allowed'
            }`}
          >
            <span>START SESSION</span>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      ) : (
        <div className="text-center py-4">
          <p className="text-text-muted text-sm mb-3">오늘의 루틴이 없습니다.</p>
          <button
            onClick={() => navigate('/routine/list')}
            className="px-4 py-2 rounded-token text-sm font-medium bg-primary-500 text-bg-root hover:opacity-90 transition-colors"
          >
            루틴 만들기
          </button>
        </div>
      )}

      {/* Exercise Recognition Modal */}
      {selectedExercise && (
        <ExerciseRecognitionModal
          isOpen={isModalOpen}
          onClose={handleModalClose}
          exerciseName={selectedExercise.name}
          exercise={{
            ...selectedExercise,
            routineId: routine.id,
          }}
        />
      )}
    </div>
  );
}

