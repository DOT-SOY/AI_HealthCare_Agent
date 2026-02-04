import { useState, useMemo, useCallback, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useRoutines } from '../../hooks/useRoutines';
import { useExercises } from '../../hooks/useExercises';
import { routineApi } from '../../api/routineApi';
import WeeklyCalendar from '../../components/routine/WeeklyCalendar';
import AISummaryCard from '../../components/routine/AISummaryCard';
import ExerciseCard from '../../components/routine/ExerciseCard';
import ExerciseEditModal from '../../components/routine/ExerciseEditModal';

export default function TodayRoutinePage() {
  const { todayRoutine, weekRoutines, loading, fetchRoutineByDate, fetchTodayRoutine, fetchWeekRoutines } = useRoutines();
  const [searchParams] = useSearchParams();

  const createDateFromString = (dateStr) => {
    const [y, m, d] = dateStr.split('-').map(Number);
    return new Date(y, m - 1, d);
  };
  const { addExercise } = useExercises();
  const [selectedDate, setSelectedDate] = useState(() => {
    const param = searchParams.get('date');
    if (param) {
      return createDateFromString(param);
    }
    return new Date();
  });
  const [activeExerciseId, setActiveExerciseId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0); // 강제 리렌더링을 위한 키
  const [isAddExerciseModalOpen, setIsAddExerciseModalOpen] = useState(false);
  const [fetchedRoutine, setFetchedRoutine] = useState(null); // 직접 가져온 루틴

  const formatDateKey = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };

  // URL 파라미터가 변경되면 selectedDate 업데이트
  useEffect(() => {
    const param = searchParams.get('date');
    if (param) {
      const newDate = createDateFromString(param);
      setSelectedDate(newDate);
      setFetchedRoutine(null); // 새로운 날짜면 초기화
    }
  }, [searchParams]);

  // 선택된 날짜의 루틴을 가져오기
  useEffect(() => {
    const loadRoutineForDate = async () => {
      const selectedDateStr = formatDateKey(selectedDate);
      const todayStr = formatDateKey(new Date());
      
      // 오늘 날짜면 todayRoutine 사용 (이미 로드됨)
      if (selectedDateStr === todayStr) {
        setFetchedRoutine(null);
        return;
      }
      
      // 주간 루틴에 있으면 사용
      const foundInWeek = weekRoutines.find(r => {
        if (!r || !r.date) return false;
        const routineDateStr = typeof r.date === 'string' 
          ? r.date 
          : formatDateKey(new Date(r.date));
        return routineDateStr === selectedDateStr;
      });
      
      if (foundInWeek) {
        setFetchedRoutine(null);
        return;
      }
      
      // 주간 루틴에 없으면 직접 가져오기
      try {
        const routine = await fetchRoutineByDate(selectedDateStr);
        setFetchedRoutine(routine);
      } catch (error) {
        console.error('루틴 조회 실패:', error);
        setFetchedRoutine(null);
      }
    };
    
    loadRoutineForDate();
  }, [selectedDate, todayRoutine, weekRoutines, fetchRoutineByDate]);

  // 선택된 날짜에 따라 displayRoutine 계산 (Redux 상태에서 직접 계산, 메모이제이션)
  const displayRoutine = useMemo(() => {
    const selectedDateStr = formatDateKey(selectedDate);
    const todayStr = formatDateKey(new Date());
    
    // 오늘 날짜면 todayRoutine 반환
    if (selectedDateStr === todayStr && todayRoutine) {
      return todayRoutine;
    }
    
    // 주간 루틴에서 찾기 (날짜 형식 정규화)
    const found = weekRoutines.find(r => {
      if (!r || !r.date) return false;
      // date가 문자열이면 그대로 비교, Date 객체면 변환
      const routineDateStr = typeof r.date === 'string' 
        ? r.date 
        : formatDateKey(new Date(r.date));
      return routineDateStr === selectedDateStr;
    });
    
    // 주간 루틴에 없으면 직접 가져온 루틴 사용
    if (!found && fetchedRoutine) {
      return fetchedRoutine;
    }
    
    return found || null;
  }, [selectedDate, todayRoutine, weekRoutines, fetchedRoutine, refreshKey]);


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
        const selectedDateStr = formatDateKey(selectedDate);
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
    <div className="w-full">
      {/* 헤더 */}
      <header className="section-header-token">
        <h1 className="section-title">
          <span className="text-text-main">Today's </span>
          <span className="text-primary-500">Routine</span>
        </h1>
        {displayRoutine?.title && (
          <p className="section-desc">{displayRoutine.title}</p>
        )}
      </header>

      {/* 주간 캘린더 */}
      <WeeklyCalendar 
        routines={weekRoutines} 
        selectedDate={selectedDate}
        onDateChange={handleDateChange}
      />

      {loading && !displayRoutine ? (
        <div className="flex flex-col items-center justify-center py-12 gap-token-4">
          <div className="spinner-token" />
          <p className="text-text-sub font-medium">로딩 중...</p>
        </div>
      ) : !displayRoutine ? (
        <>
          {/* 루틴이 없을 때도 운동 추가 가능 */}
          <div className="flex flex-col items-center justify-center py-12 mb-6">
            <p className="text-text-muted text-lg mb-4">
              {selectedDate.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })}에 루틴이 없습니다.
            </p>
            <p className="text-text-muted text-sm mb-6">
              새로운 운동을 추가하면 루틴이 자동으로 생성됩니다.
            </p>
          </div>
          
          {/* 운동 추가 버튼 */}
          <button
            onClick={() => setIsAddExerciseModalOpen(true)}
            type="button"
            className="w-full bg-bg-card border-2 border-dashed border-border-default rounded-token p-6 transition-all duration-200 hover:border-primary-500 hover:text-primary-500 text-text-sub"
          >
            <div className="flex items-center justify-center gap-3">
              <svg className="w-6 h-6 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span className="font-medium">새로운 운동 추가</span>
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
              <div className="text-center text-text-muted py-8">
                <p>이 루틴에 운동이 없습니다.</p>
                <p className="text-sm mt-2">새로운 운동을 추가해주세요.</p>
                <p className="text-xs mt-1 text-text-muted">
                  (루틴 ID: {displayRoutine.id}, 운동 개수: {displayRoutine.exercises ? displayRoutine.exercises.length : 'undefined'})
                </p>
              </div>
            )}
            
            {/* 운동 추가 버튼 */}
            <button
              onClick={() => setIsAddExerciseModalOpen(true)}
              type="button"
              className="w-full bg-bg-card border-2 border-dashed border-border-default rounded-token p-6 transition-all duration-200 hover:border-primary-500 hover:text-primary-500 text-text-sub"
            >
              <div className="flex items-center justify-center gap-3">
                <svg className="w-6 h-6 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                <span className="font-medium">새로운 운동 추가</span>
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

