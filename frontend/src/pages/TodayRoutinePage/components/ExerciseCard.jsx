import { useState } from 'react';
import { useExercises } from '../../../hooks/useExercises';
import ExerciseEditModal from './ExerciseEditModal';

export default function ExerciseCard({ exercise, routineId, isActive = false, onStart, onComplete, onUpdate }) {
  const { toggleCompleted, updateExercise, deleteExercise } = useExercises();
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  
  const isExerciseCompleted = exercise.completed;

  const handleToggleCompleted = async () => {
    try {
      // Optimistic Update는 useExercises 내부에서 처리됨
      await toggleCompleted(routineId, exercise.id);
    } catch (error) {
      console.error('운동 완료 토글 실패:', error);
      // 에러는 useExercises에서 이미 롤백 처리됨
    }
  };

  const handleSave = async (updatedData) => {
    try {
      await updateExercise(routineId, exercise.id, updatedData);
      // Redux 상태가 자동으로 업데이트되므로 onUpdate 불필요
    } catch (error) {
      console.error('운동 수정 실패:', error);
    }
  };

  const handleDelete = async () => {
    try {
      await deleteExercise(routineId, exercise.id);
      setIsDeleteConfirmOpen(false);
      // Redux 상태가 자동으로 업데이트되므로 onUpdate 불필요
    } catch (error) {
      console.error('운동 삭제 실패:', error);
    }
  };

  const handleStartExercise = () => {
    setIsAnalyzing(true);
    if (onStart) onStart();
  };

  const handleCompleteSet = () => {
    setIsAnalyzing(false);
    if (onComplete) onComplete();
  };

  return (
    <div
      className={`bg-neutral-800 rounded-lg p-6 ${
        isActive 
          ? 'border-2 border-neon-green shadow-[0_0_20px_rgba(0,255,65,0.3)]' 
          : isExerciseCompleted 
            ? 'border-2 border-neon-green/50 bg-neutral-800/80' 
            : ''
      }`}
    >
      <div className="flex items-start justify-between">
        {/* 왼쪽: 운동 정보 */}
        <div className="flex items-start gap-4 flex-1">
          {/* 아이콘 */}
          <div
            className={`w-12 h-12 rounded-full flex items-center justify-center ${
              isActive 
                ? 'bg-neon-green' 
                : isExerciseCompleted 
                  ? 'bg-neon-green/20 border-2 border-neon-green' 
                  : 'bg-neutral-700'
            }`}
          >
            {isActive ? (
              <svg className="w-6 h-6 text-neutral-950" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            ) : isExerciseCompleted ? (
              <svg className="w-6 h-6 text-neon-green" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
              </svg>
            ) : (
              <svg className="w-6 h-6 text-neutral-400" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
            )}
          </div>

          {/* 운동 정보 */}
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-2">
              <h3 className={`text-xl font-semibold ${isExerciseCompleted ? 'text-neutral-400 line-through' : 'text-neutral-50'}`}>
                {exercise.name}
              </h3>
              {isExerciseCompleted && (
                <span className="text-neon-green text-sm font-medium flex items-center gap-1">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  완료
                </span>
              )}
            </div>
            <p className="text-neutral-400">
              Sets: {exercise.sets} Reps: {exercise.reps} {exercise.weight != null ? `Weight: ${exercise.weight}kg` : 'Weight: -'}
            </p>

            {/* 활성화된 운동의 분석 박스 */}
            {isActive && (
              <div className="mt-4 bg-neon-green rounded-lg p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <svg className="w-5 h-5 text-neutral-950" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    <span className="text-neutral-950 font-medium">자세를 분석하는 중입니다...</span>
                  </div>
                  <div className="text-neutral-950 font-bold text-2xl">··· 8회</div>
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <svg className="w-4 h-4 text-red-500" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" />
                  </svg>
                  <span className="text-neutral-950 text-sm">팔꿈치 각도에 주의하세요.</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 오른쪽: 버튼 */}
        <div className="flex flex-col gap-2">
          {isActive && isAnalyzing ? (
            <button
              onClick={handleCompleteSet}
              className="bg-neon-green text-neutral-950 px-4 py-2 rounded-lg font-medium hover:bg-neon-green/80 transition-colors"
            >
              세트 완료
            </button>
          ) : isActive ? (
            <button
              onClick={handleCompleteSet}
              className="bg-neon-green text-neutral-950 px-4 py-2 rounded-lg font-medium hover:bg-neon-green/80 transition-colors"
            >
              세트 완료
            </button>
          ) : (
            <>
              <button
                onClick={handleStartExercise}
                className="px-4 py-2 rounded-lg font-medium bg-neon-green text-neutral-950 hover:bg-neon-green/80 transition-colors"
              >
                ▷ 세트 시작
              </button>
              <button
                onClick={handleToggleCompleted}
                className={`px-4 py-2 rounded-lg font-medium transition-colors text-sm ${
                  isExerciseCompleted
                    ? 'bg-neon-green text-neutral-950 hover:bg-neon-green/80'
                    : 'bg-neutral-700 text-neutral-300 hover:bg-neutral-600'
                }`}
              >
                {isExerciseCompleted ? '✓ 완료됨' : '완료'}
              </button>
              <button
                onClick={() => setIsEditModalOpen(true)}
                className="px-4 py-2 rounded-lg font-medium bg-neutral-700 text-neutral-300 hover:bg-neutral-600 transition-colors text-sm"
              >
                수정
              </button>
              <button
                onClick={() => setIsDeleteConfirmOpen(true)}
                className="px-4 py-2 rounded-lg font-medium bg-red-600/20 text-red-400 hover:bg-red-600/30 transition-colors text-sm border border-red-600/30"
              >
                삭제
              </button>
            </>
          )}
        </div>
      </div>

      <ExerciseEditModal
        exercise={exercise}
        isOpen={isEditModalOpen}
        onClose={() => setIsEditModalOpen(false)}
        onSave={handleSave}
      />

      {/* 삭제 확인 모달 */}
      {isDeleteConfirmOpen && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-neutral-800 rounded-lg p-6 w-96">
            <h3 className="text-xl font-semibold text-neutral-50 mb-4">운동 삭제</h3>
            <p className="text-neutral-300 mb-6">
              정말로 <span className="text-neon-green font-medium">{exercise.name}</span> 운동을 삭제하시겠습니까?
              <br />
              <span className="text-sm text-neutral-400">이 작업은 되돌릴 수 없습니다.</span>
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setIsDeleteConfirmOpen(false)}
                className="px-4 py-2 rounded-lg font-medium bg-neutral-700 text-neutral-300 hover:bg-neutral-600 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 rounded-lg font-medium bg-red-600 text-neutral-50 hover:bg-red-700 transition-colors"
              >
                삭제
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
