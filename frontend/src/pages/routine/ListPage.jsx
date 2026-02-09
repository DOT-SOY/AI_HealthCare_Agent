import { useState, useMemo, useCallback, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useRoutines } from '../../hooks/useRoutines';
import { useExercises } from '../../hooks/useExercises';
import { useWebSocket } from '../../hooks/useWebSocket';
import { routineApi } from '../../api/routineApi';
import WeeklyCalendar from '../../components/routine/WeeklyCalendar';
import AISummaryCard from '../../components/routine/AISummaryCard';
import ExerciseCard from '../../components/routine/ExerciseCard';
import ExerciseEditModal from '../../components/routine/ExerciseEditModal';
import LoadingModal from '../../components/common/LoadingModal';

export default function TodayRoutinePage() {
  const location = useLocation();
  const { todayRoutine, weekRoutines, loading, fetchRoutineByDate, fetchTodayRoutine, fetchWeekRoutines } = useRoutines();
  const { addExercise } = useExercises();
  const { connectWebSocket, subscribeToRoutineUpdate } = useWebSocket();
  // URL 쿼리에서 date=YYYY-MM-DD가 있으면 그 날짜를 기본 선택 날짜로 사용
  const getInitialSelectedDate = () => {
    try {
      const params = new URLSearchParams(location.search);
      const dateParam = params.get('date');
      if (dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam)) {
        const parsed = new Date(`${dateParam}T00:00:00`);
        if (!isNaN(parsed.getTime())) {
          return parsed;
        }
      }
    } catch (e) {
      console.warn('선택 날짜 파싱 실패:', e);
    }
    return new Date();
  };

  const [selectedDate, setSelectedDate] = useState(getInitialSelectedDate);

  const [showYearDropdown, setShowYearDropdown] = useState(false);
  const [showMonthDropdown, setShowMonthDropdown] = useState(false);
  const [showDayDropdown, setShowDayDropdown] = useState(false);
  const [activeExerciseId, setActiveExerciseId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [isAddExerciseModalOpen, setIsAddExerciseModalOpen] = useState(false);
  const [dateRoutine, setDateRoutine] = useState(null); // URL/선택 날짜용 개별 루틴

  // 로컬 기준 YYYY-MM-DD 키 생성 (UTC toISOString 사용으로 인한 1일 차이 방지)
  const toLocalDateKey = (d) => {
    const dd = d instanceof Date ? d : new Date(d);
    const y = dd.getFullYear();
    const m = String(dd.getMonth() + 1).padStart(2, '0');
    const day = String(dd.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  const selectedDateObj = useMemo(
    () => (selectedDate instanceof Date ? selectedDate : new Date(selectedDate || Date.now())),
    [selectedDate]
  );

  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 5 }, (_, i) => currentYear - 2 + i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);
  const getDaysInMonth = (year, month) => new Date(year, month, 0).getDate();
  const days = useMemo(
    () => Array.from({ length: getDaysInMonth(selectedDateObj.getFullYear(), selectedDateObj.getMonth() + 1) }, (_, i) => i + 1),
    [selectedDateObj.getFullYear(), selectedDateObj.getMonth()]
  );
  const formatDateForApi = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };

  const displayRoutine = useMemo(() => {
    const selectedDateStr = formatDateForApi(selectedDateObj);
    const todayStr = formatDateForApi(new Date());

    if (selectedDateStr === todayStr && todayRoutine) {
      return todayRoutine;
    }

    const found = weekRoutines.find(r => {
      if (!r || !r.date) return false;
      const routineDateStr = typeof r.date === 'string' 
        ? (r.date.includes('T') ? r.date.split('T')[0] : r.date)
        : toLocalDateKey(r.date);
      return routineDateStr === selectedDateStr;
    });

    if (found) return found;

    // 주간 루틴에 없으면, 개별 조회한 루틴(dateRoutine) 사용
    if (dateRoutine && dateRoutine.date) {
      const routineDateStr = typeof dateRoutine.date === 'string'
        ? (dateRoutine.date.includes('T') ? dateRoutine.date.split('T')[0] : dateRoutine.date)
        : toLocalDateKey(dateRoutine.date);
      if (routineDateStr === selectedDateStr) {
        return dateRoutine;
      }
    }

    return null;
  }, [selectedDate, todayRoutine, weekRoutines, dateRoutine, refreshKey]);

  // URL 쿼리(date)가 변경되면 선택 날짜를 동기화
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const dateParam = params.get('date');
    if (dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam)) {
      const parsed = new Date(`${dateParam}T00:00:00`);
      if (!isNaN(parsed.getTime())) {
        setSelectedDate(parsed);
      }
    }
  }, [location.search]);

  // 선택된 날짜 기준으로 개별 루틴 데이터 로딩 (오늘/이번 주에 없을 때를 대비)
  useEffect(() => {
    const fetchByDate = async () => {
      const selectedDateStr = toLocalDateKey(selectedDate);
      const todayStr = toLocalDateKey(new Date());

      // 오늘은 todayRoutine/weekRoutines를 우선 사용
      if (selectedDateStr === todayStr) {
        setDateRoutine(null);
        return;
      }

      const data = await fetchRoutineByDate(selectedDateStr);
      setDateRoutine(data || null);
    };

    fetchByDate();
  }, [selectedDate, fetchRoutineByDate]);


  const handleRoutineUpdate = useCallback(async () => {
    await fetchTodayRoutine();
    await fetchWeekRoutines();
    setRefreshKey(prev => prev + 1);
  }, [fetchTodayRoutine, fetchWeekRoutines]);

  useEffect(() => {
    connectWebSocket();
    subscribeToRoutineUpdate(handleRoutineUpdate);
    const onRoutineUpdated = () => handleRoutineUpdate();
    window.addEventListener('routine-updated', onRoutineUpdated);
    return () => window.removeEventListener('routine-updated', onRoutineUpdated);
  }, [connectWebSocket, subscribeToRoutineUpdate, handleRoutineUpdate]);

  const handleDateChange = (type, value) => {
    const newDate = new Date(selectedDateObj);
    if (type === 'year') newDate.setFullYear(value);
    else if (type === 'month') newDate.setMonth(value - 1);
    else if (type === 'day') newDate.setDate(value);
    const maxDay = getDaysInMonth(newDate.getFullYear(), newDate.getMonth() + 1);
    if (newDate.getDate() > maxDay) newDate.setDate(maxDay);
    setSelectedDate(newDate);
    setShowYearDropdown(false);
    setShowMonthDropdown(false);
    setShowDayDropdown(false);
  };

  const handleExerciseStart = (exerciseId) => {
    setActiveExerciseId(exerciseId);
  };

  const handleExerciseComplete = () => {
    setActiveExerciseId(null);
    fetchTodayRoutine();
  };

  const handleAddExercise = async (exerciseData) => {
    try {
      let routineId = displayRoutine?.id;
      if (!routineId) {
        const selectedDateStr = formatDateForApi(selectedDateObj);
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
      const message = error?.response?.data?.message ?? error?.message ?? '운동 추가에 실패했습니다.';
      if (message.includes('이미 같은 운동') || message.includes('같은 운동이 있습니다')) {
        alert('이 루틴에 이미 같은 운동이 있어요. 같은 날에는 같은 운동을 두 번 넣을 수 없어요.');
      } else {
        alert(message);
      }
    }
  };

  return (
    <div className="w-full">
      {/* 헤더 */}
      <header className="section-header-token flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4 mb-6">
        <div>
          <h1 className="section-title">
            <span className="text-text-main">Today's </span>
            <span className="text-primary-500">Routine</span>
          </h1>
          {displayRoutine?.title && (
            <p className="section-desc mt-1">{displayRoutine.title}</p>
          )}
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <div
            className="segment-btn relative cursor-pointer"
            onClick={() => { setShowYearDropdown(!showYearDropdown); setShowMonthDropdown(false); setShowDayDropdown(false); }}
          >
            {selectedDateObj.getFullYear()}년
            {showYearDropdown && (
              <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[100px]">
                {years.map((year) => (
                  <div
                    key={year}
                    className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                    onClick={(e) => { e.stopPropagation(); handleDateChange('year', year); }}
                  >
                    {year}년
                  </div>
                ))}
              </div>
            )}
          </div>
          <div
            className="segment-btn relative cursor-pointer"
            onClick={() => { setShowMonthDropdown(!showMonthDropdown); setShowYearDropdown(false); setShowDayDropdown(false); }}
          >
            {selectedDateObj.getMonth() + 1}월
            {showMonthDropdown && (
              <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[80px]">
                {months.map((month) => (
                  <div
                    key={month}
                    className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                    onClick={(e) => { e.stopPropagation(); handleDateChange('month', month); }}
                  >
                    {month}월
                  </div>
                ))}
              </div>
            )}
          </div>
          <div
            className="segment-btn relative cursor-pointer"
            onClick={() => { setShowDayDropdown(!showDayDropdown); setShowYearDropdown(false); setShowMonthDropdown(false); }}
          >
            {selectedDateObj.getDate()}일
            {showDayDropdown && (
              <div className="absolute top-full left-0 mt-2 bg-bg-card border border-border-default rounded-token shadow-lg z-50 max-h-48 overflow-y-auto min-w-[80px]">
                {days.map((day) => (
                  <div
                    key={day}
                    className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-text-main text-sm"
                    onClick={(e) => { e.stopPropagation(); handleDateChange('day', day); }}
                  >
                    {day}일
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </header>

      {loading && !displayRoutine ? (
        <LoadingModal isOpen={true} message="로딩 중..." />
      ) : !displayRoutine ? (
        <>
          {/* 루틴이 없을 때도 운동 추가 가능 */}
          <div className="flex flex-col items-center justify-center py-12 mb-6">
            <div className="text-text-muted text-lg mb-4">
              {selectedDateObj.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })}에 루틴이 없습니다.
            </div>
            <p className="text-text-muted text-sm mb-6">
              새로운 운동을 추가하면 루틴이 자동으로 생성됩니다.
            </p>
          </div>
          
          {/* 운동 추가 버튼 */}
          <button
            onClick={() => setIsAddExerciseModalOpen(true)}
            className="w-full bg-bg-card hover:bg-bg-surface border-2 border-dashed border-border-default rounded-lg p-6 transition-colors hover:border-primary-500"
          >
            <div className="flex items-center justify-center gap-3">
              <svg className="w-6 h-6 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span className="text-text-sub font-medium">새로운 운동 추가</span>
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
              (() => {
                // 완료된 운동과 미완료 운동 분리
                const incompleteExercises = displayRoutine.exercises.filter(ex => !ex.completed);
                const completedExercises = displayRoutine.exercises.filter(ex => ex.completed);

                // 미완료 항목을 위로, 완료 항목을 아래로 배치
                const sortedExercises = [...incompleteExercises, ...completedExercises];

                return sortedExercises.map((exercise, index) => (
                  <ExerciseCard
                    key={exercise.id || index}
                    exercise={exercise}
                    routineId={displayRoutine.id}
                    isActive={activeExerciseId === exercise.id}
                    onStart={() => handleExerciseStart(exercise.id)}
                    onComplete={handleExerciseComplete}
                    onUpdate={handleRoutineUpdate}
                  />
                ));
              })()
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
              className="w-full bg-bg-card hover:bg-bg-surface border-2 border-dashed border-border-default rounded-lg p-6 transition-colors hover:border-primary-500"
            >
              <div className="flex items-center justify-center gap-3">
                <svg className="w-6 h-6 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                <span className="text-text-sub font-medium">새로운 운동 추가</span>
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
