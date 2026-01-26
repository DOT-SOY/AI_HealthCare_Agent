import { useState, useMemo, useCallback } from 'react';
import { useRoutines } from '../../hooks/useRoutines';
import { useMember } from '../../hooks/useMember';
import { useExercises } from '../../hooks/useExercises';
import { routineApi } from '../../api/routineApi';
import WeeklyCalendar from './components/WeeklyCalendar';
import AISummaryCard from './components/AISummaryCard';
import ExerciseCard from './components/ExerciseCard';
import ExerciseEditModal from './components/ExerciseEditModal';

export default function TodayRoutinePage() {
  const { todayRoutine, weekRoutines, loading, fetchRoutineByDate, fetchTodayRoutine, fetchWeekRoutines } = useRoutines();
  const { member } = useMember();
  const { addExercise } = useExercises();
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [activeExerciseId, setActiveExerciseId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0); // 강제 리렌더링을 위한 키
  const [isAddExerciseModalOpen, setIsAddExerciseModalOpen] = useState(false);

  // 선택된 날짜에 따라 displayRoutine 계산 (Redux 상태에서 직접 계산, 메모이제이션)
  const displayRoutine = useMemo(() => {
    const selectedDateStr = selectedDate.toISOString().split('T')[0];
    const todayStr = new Date().toISOString().split('T')[0];
    
    // 오늘 날짜면 todayRoutine 반환
    if (selectedDateStr === todayStr && todayRoutine) {
      console.log('오늘 루틴:', todayRoutine);
      return todayRoutine;
    }
    
    // 주간 루틴에서 찾기 (날짜 형식 정규화)
    const found = weekRoutines.find(r => {
      if (!r || !r.date) return false;
      // date가 문자열이면 그대로 비교, Date 객체면 변환
      const routineDateStr = typeof r.date === 'string' 
        ? r.date 
        : new Date(r.date).toISOString().split('T')[0];
      return routineDateStr === selectedDateStr;
    });
    
    if (found) {
      console.log('찾은 루틴:', found);
      console.log('운동 개수:', found.exercises?.length || 0);
      console.log('운동 데이터:', found.exercises);
    } else {
      console.log('루틴을 찾을 수 없습니다. selectedDateStr:', selectedDateStr);
      console.log('주간 루틴 목록:', weekRoutines);
    }
    
    return found || null;
  }, [selectedDate, todayRoutine, weekRoutines, refreshKey]);


  // 루틴 업데이트 핸들러 (완료 버튼 클릭 시 호출)
  const handleRoutineUpdate = useCallback(async () => {
    // 항상 오늘의 루틴과 주간 루틴 모두 새로고침
    await fetchTodayRoutine();
    await fetchWeekRoutines();
    // 강제 리렌더링 트리거
    setRefreshKey(prev => prev + 1);
  }, [fetchTodayRoutine, fetchWeekRoutines]);

  const handleDateChange = (date) => {
    setSelectedDate(date);
  };

  const handleExerciseStart = (exerciseId) => {
    setActiveExerciseId(exerciseId);
  };

  const handleExerciseComplete = () => {
    setActiveExerciseId(null);
    // 루틴 데이터 새로고침
    fetchTodayRoutine();
  };

  const handleAddExercise = async (exerciseData) => {
    try {
      // 루틴이 없으면 먼저 생성
      let routineId = displayRoutine?.id;
      if (!routineId) {
        const selectedDateStr = selectedDate.toISOString().split('T')[0];
        const newRoutine = await routineApi.create(
          selectedDateStr,
          '새로운 루틴',
          ''
        );
        routineId = newRoutine.id;
        await handleRoutineUpdate();
      }
      
      await addExercise(routineId, exerciseData);
      await handleRoutineUpdate();
      setIsAddExerciseModalOpen(false);
    } catch (error) {
      console.error('운동 추가 실패:', error);
    }
  };

  return (
    <div className="ml-20 p-8 min-h-screen">
      {/* 헤더 */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-4xl font-bold mb-2">
              <span className="text-neutral-50">Today's </span>
              <span className="text-neon-green">Routine</span>
            </h1>
            <p className="text-neutral-400 text-lg">
              {displayRoutine?.title || 'HYPERTROPHY PUSH DAY'}
            </p>
          </div>
          {member && (
            <div className="text-right">
              <p className="text-neutral-400 text-sm">{member.name}님</p>
              <p className="text-neutral-500 text-xs">{member.target || 'BULK'}</p>
            </div>
          )}
        </div>
      </div>

      {/* 주간 캘린더 */}
      <WeeklyCalendar 
        routines={weekRoutines} 
        selectedDate={selectedDate}
        onDateChange={handleDateChange}
      />

      {loading && !displayRoutine ? (
        <div className="flex items-center justify-center py-12">
          <div className="text-neutral-400">로딩 중...</div>
        </div>
      ) : !displayRoutine ? (
        <>
          {/* 루틴이 없을 때도 운동 추가 가능 */}
          <div className="flex flex-col items-center justify-center py-12 mb-6">
            <div className="text-neutral-400 text-lg mb-4">
              {selectedDate.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })}에 루틴이 없습니다.
            </div>
            <p className="text-neutral-500 text-sm mb-6">
              새로운 운동을 추가하면 루틴이 자동으로 생성됩니다.
            </p>
          </div>
          
          {/* 운동 추가 버튼 */}
          <button
            onClick={() => setIsAddExerciseModalOpen(true)}
            className="w-full bg-neutral-700 hover:bg-neutral-600 border-2 border-dashed border-neutral-600 hover:border-neon-green rounded-lg p-6 transition-colors"
          >
            <div className="flex items-center justify-center gap-3">
              <svg className="w-6 h-6 text-neutral-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span className="text-neutral-300 font-medium">새로운 운동 추가</span>
            </div>
          </button>

          {/* 운동 추가 모달 */}
          <ExerciseEditModal
            exercise={null}
            isOpen={isAddExerciseModalOpen}
            onClose={() => setIsAddExerciseModalOpen(false)}
            onSave={handleAddExercise}
          />
        </>
      ) : (
        <>
          {/* AI 코칭 요약 */}
          <AISummaryCard routine={displayRoutine} />

          {/* 운동 목록 */}
          <div className="space-y-4 mt-6">
            {displayRoutine.exercises && Array.isArray(displayRoutine.exercises) && displayRoutine.exercises.length > 0 ? (
              displayRoutine.exercises.map((exercise, index) => (
                <ExerciseCard
                  key={exercise.id || index}
                  exercise={exercise}
                  routineId={displayRoutine.id}
                  isActive={activeExerciseId === exercise.id}
                  onStart={() => handleExerciseStart(exercise.id)}
                  onComplete={handleExerciseComplete}
                  onUpdate={handleRoutineUpdate}
                />
              ))
            ) : (
              <div className="text-center text-neutral-400 py-8">
                <p>이 루틴에 운동이 없습니다.</p>
                <p className="text-sm mt-2">새로운 운동을 추가해주세요.</p>
                <p className="text-xs mt-1 text-neutral-500">
                  (루틴 ID: {displayRoutine.id}, 운동 개수: {displayRoutine.exercises ? displayRoutine.exercises.length : 'undefined'})
                </p>
              </div>
            )}
            
            {/* 운동 추가 버튼 */}
            <button
              onClick={() => setIsAddExerciseModalOpen(true)}
              className="w-full bg-neutral-700 hover:bg-neutral-600 border-2 border-dashed border-neutral-600 hover:border-neon-green rounded-lg p-6 transition-colors"
            >
              <div className="flex items-center justify-center gap-3">
                <svg className="w-6 h-6 text-neutral-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                <span className="text-neutral-300 font-medium">새로운 운동 추가</span>
              </div>
            </button>
          </div>

          {/* 운동 추가 모달 */}
          <ExerciseEditModal
            exercise={null}
            isOpen={isAddExerciseModalOpen}
            onClose={() => setIsAddExerciseModalOpen(false)}
            onSave={handleAddExercise}
          />
        </>
      )}
    </div>
  );
}

