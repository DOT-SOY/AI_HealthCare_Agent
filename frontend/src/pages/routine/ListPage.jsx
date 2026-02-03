import { useState, useMemo, useCallback, useEffect } from 'react';
import { useRoutines } from '../../hooks/useRoutines';
import { useExercises } from '../../hooks/useExercises';
import { useWebSocket } from '../../hooks/useWebSocket';
import { routineApi } from '../../api/routineApi';
import WeeklyCalendar from '../../components/routine/WeeklyCalendar';
import AISummaryCard from '../../components/routine/AISummaryCard';
import ExerciseCard from '../../components/routine/ExerciseCard';
import ExerciseEditModal from '../../components/routine/ExerciseEditModal';

// API 실패 시에도 카드가 보이도록 백엔드와 동일한 기본 프리셋 (분할 4일, 상하체 2일)
const FALLBACK_PRESETS = [
  {
    groupName: '분할 루틴',
    days: [
      { title: 'Push Day', summary: '가슴, 어깨, 삼두근을 사용하는 날입니다.', exerciseNames: ['벤치프레스', '오버헤드프레스'] },
      { title: 'Pull Day', summary: '등, 이두근, 후면 사슬을 사용하는 날입니다.', exerciseNames: ['데드리프트', '바벨 컬'] },
      { title: 'Leg Day', summary: '허벅지 앞/뒤, 엉덩이, 종아리를 사용하는 날입니다.', exerciseNames: ['스쿼트', '힙쓰러스트', '카프레이즈'] },
      { title: 'Core Day', summary: '복부와 허리, 몸의 중심을 지탱하는 코어 근육을 사용하는 날입니다.', exerciseNames: ['플랭크', '행잉레그레이즈'] },
    ],
  },
  {
    groupName: '상하체 루틴',
    days: [
      { title: 'Upper Day', summary: '가슴, 어깨, 팔, 그리고 복근을 단련합니다.', exerciseNames: ['벤치프레스', '오버헤드프레스', '바벨 컬', '행잉레그레이즈', '플랭크'] },
      { title: 'Leg Day', summary: '허벅지, 엉덩이, 종아리, 등 하부(후면 사슬)를 단련합니다.', exerciseNames: ['스쿼트', '데드리프트', '힙쓰러스트', '카프레이즈'] },
    ],
  },
];

export default function TodayRoutinePage() {
  const { todayRoutine, weekRoutines, loading, fetchRoutineByDate, fetchTodayRoutine, fetchWeekRoutines } = useRoutines();
  const { addExercise } = useExercises();
  const { subscribeToRoutineGenerate, connectWebSocket } = useWebSocket();
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [activeExerciseId, setActiveExerciseId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0); // 강제 리렌더링을 위한 키
  const [isAddExerciseModalOpen, setIsAddExerciseModalOpen] = useState(false);
  const [isPresetModalOpen, setIsPresetModalOpen] = useState(false);
  const [presets, setPresets] = useState([]);
  const [presetsLoading, setPresetsLoading] = useState(false);
  const [applyLoading, setApplyLoading] = useState(false);

  // 선택된 날짜에 따라 displayRoutine 계산 (Redux 상태에서 직접 계산, 메모이제이션)
  const displayRoutine = useMemo(() => {
    const selectedDateStr = selectedDate.toISOString().split('T')[0];
    const todayStr = new Date().toISOString().split('T')[0];
    
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
        : new Date(r.date).toISOString().split('T')[0];
      return routineDateStr === selectedDateStr;
    });
    
    return found || null;
  }, [selectedDate, todayRoutine, weekRoutines, refreshKey]);

  // AI 루틴 생성 완료 시 WebSocket으로 수신 → 오늘/주간 루틴 자동 갱신
  useEffect(() => {
    connectWebSocket();
    subscribeToRoutineGenerate(() => {
      fetchTodayRoutine();
      fetchWeekRoutines();
      setRefreshKey((k) => k + 1);
    });
  }, [connectWebSocket, subscribeToRoutineGenerate, fetchTodayRoutine, fetchWeekRoutines]);

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

  const openPresetModal = useCallback(async () => {
    setIsPresetModalOpen(true);
    setPresetsLoading(true);
    try {
      const data = await routineApi.getPresets();
      const list = Array.isArray(data) ? data : (data?.content ?? data?.data ?? []);
      setPresets(Array.isArray(list) && list.length > 0 ? list : FALLBACK_PRESETS);
    } catch (err) {
      console.error('프리셋 조회 실패:', err);
      setPresets(FALLBACK_PRESETS);
    } finally {
      setPresetsLoading(false);
    }
  }, []);

  const handleApplyPreset = useCallback(async (presetIndex) => {
    setApplyLoading(true);
    try {
      const todayStr = new Date().toISOString().split('T')[0];
      await routineApi.applyPreset(todayStr, presetIndex);
      await fetchTodayRoutine();
      await fetchWeekRoutines();
      setRefreshKey((k) => k + 1);
      setIsPresetModalOpen(false);
    } catch (err) {
      console.error('프리셋 적용 실패:', err);
    } finally {
      setApplyLoading(false);
    }
  }, [fetchTodayRoutine, fetchWeekRoutines]);

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

          {/* 프리셋 카드 2개 (분할 루틴, 상하체 루틴) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
            {FALLBACK_PRESETS.map((preset, index) => (
              <button
                key={preset.groupName}
                type="button"
                disabled={applyLoading}
                onClick={() => handleApplyPreset(index)}
                className="text-left p-5 rounded-xl border-2 border-neutral-600 bg-neutral-700/50 hover:border-[#88ce02] hover:bg-neutral-700 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <div className="font-semibold text-lg mb-3 text-[#88ce02]">
                  {preset.groupName}
                </div>
                <div className="text-neutral-400 text-sm space-y-3">
                  {preset.days?.map((day, i) => (
                    <div key={i} className="border-l-2 border-neutral-600 pl-2">
                      <div className="font-medium text-neutral-300">{i + 1}. {day.title}</div>
                      <div className="text-xs mt-1 text-neutral-500">{day.exerciseNames?.join(', ')}</div>
                    </div>
                  ))}
                </div>
                {applyLoading && <div className="mt-3 text-neutral-500 text-xs">적용 중...</div>}
              </button>
            ))}
          </div>

          <p className="text-neutral-500 text-sm mb-4">또는 수동으로 운동을 추가할 수 있습니다.</p>
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

      {/* 프리셋 선택 모달 */}
      {isPresetModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
          onClick={() => !applyLoading && setIsPresetModalOpen(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="preset-modal-title"
        >
          <div
            className="bg-neutral-800 rounded-xl border border-neutral-600 shadow-xl w-full max-w-2xl mx-4 overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-6 border-b border-neutral-600 flex items-center justify-between">
              <h2 id="preset-modal-title" className="text-xl font-bold text-neutral-50">
                루틴 선택
              </h2>
              <button
                type="button"
                onClick={() => !applyLoading && setIsPresetModalOpen(false)}
                className="text-neutral-400 hover:text-neutral-50 p-1 rounded"
                aria-label="닫기"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="p-6">
              <p className="text-neutral-400 text-sm mb-6">
                오늘부터 연속된 날짜에 루틴이 자동으로 생성됩니다.
              </p>
              {presetsLoading ? (
                <div className="flex justify-center py-12 text-neutral-400">로딩 중...</div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {(presets.length > 0 ? presets : FALLBACK_PRESETS).map((preset, index) => (
                    <button
                      key={preset.groupName ?? index}
                      type="button"
                      disabled={applyLoading}
                      onClick={() => handleApplyPreset(index)}
                      className="text-left p-5 rounded-lg border-2 border-neutral-600 bg-neutral-700/50 hover:border-[#88ce02] hover:bg-neutral-700 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      <div className="font-semibold text-neutral-50 mb-3" style={{ color: '#88ce02' }}>
                        {preset.groupName}
                      </div>
                      <div className="text-neutral-400 text-sm space-y-3">
                        {preset.days?.map((day, i) => (
                          <div key={i} className="border-l-2 border-neutral-600 pl-2">
                            <div className="font-medium text-neutral-300">{i + 1}. {day.title}</div>
                            <div className="text-xs mt-1 text-neutral-500">{day.exerciseNames?.join(', ')}</div>
                          </div>
                        ))}
                      </div>
                      {applyLoading && (
                        <div className="mt-3 text-neutral-500 text-xs">적용 중...</div>
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

