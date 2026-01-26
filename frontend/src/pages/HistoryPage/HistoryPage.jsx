import { useState, useEffect } from 'react';
import { useRoutines } from '../../hooks/useRoutines';
import { useMember } from '../../hooks/useMember';
import FilterButtons from './components/FilterButtons';
import ExerciseCard from './components/ExerciseCard';
import ExerciseDetailModal from './components/ExerciseDetailModal';

// 부위별 카테고리 매핑
const bodyPartToCategory = {
  '전체': ['CHEST', 'BACK', 'SHOULDER', 'ARM', 'CORE', 'ABS', 'THIGH', 'GLUTE', 'CALF'],
  '상체': ['CHEST', 'BACK', 'SHOULDER', 'ARM', 'CORE', 'ABS'],
  '하체': ['THIGH', 'GLUTE', 'CALF'],
  '팔': ['ARM'],
  '어깨': ['SHOULDER'],
  '가슴': ['CHEST'],
  '등': ['BACK'],
  '코어': ['CORE'],
  '복근': ['ABS'],
  '둔근': ['GLUTE'],
  '허벅지': ['THIGH'],
  '종아리': ['CALF'],
};

export default function HistoryPage() {
  const { historyRoutines, fetchHistoryRoutines } = useRoutines();
  const { member } = useMember();
  const [selectedFilter, setSelectedFilter] = useState('전체');
  const [selectedExercise, setSelectedExercise] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  useEffect(() => {
    // bodyPart 파라미터로 필터링 (전체는 null로 전달)
    const bodyPart = selectedFilter === '전체' ? null :
                     selectedFilter === '상체' ? '상체' : 
                     selectedFilter === '하체' ? '하체' : selectedFilter;
    fetchHistoryRoutines(bodyPart);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFilter]);
  

  // 선택된 부위에 해당하는 운동만 필터링 (완료된 운동이 있는 루틴만)
  // 서브 타겟도 고려하여 필터링
  const categories = bodyPartToCategory[selectedFilter] || [];
  const filteredRoutines = selectedFilter === '전체' 
    ? historyRoutines // 전체는 필터링 없이 모든 루틴 표시
    : historyRoutines.filter((routine) => {
    if (!routine.exercises || routine.exercises.length === 0) {
      return false;
    }
    const hasMatchingCompletedExercise = routine.exercises.some(ex => {
      const isCompleted = ex.completed === true;
      if (!isCompleted) return false;
      
      // 메인 타겟 확인
      const matchesMainTarget = ex.mainTarget && categories.includes(ex.mainTarget);
      
      // 서브 타겟 확인
      const matchesSubTarget = ex.subTargets && ex.subTargets.some(sub => categories.includes(sub));
      
      return matchesMainTarget || matchesSubTarget;
    });
    return hasMatchingCompletedExercise;
  });

  // 운동별로 그룹화 (전체 기록에서)
  const exerciseGroups = {};
  filteredRoutines.forEach((routine) => {
    if (!routine.exercises || routine.exercises.length === 0) {
      return;
    }
    
    routine.exercises.forEach((exercise) => {
      if (!exercise.completed) {
        return;
      }
      // 필터링: 메인 타겟 또는 서브 타겟 확인
      if (selectedFilter !== '전체') {
        const matchesMainTarget = exercise.mainTarget && categories.includes(exercise.mainTarget);
        const matchesSubTarget = exercise.subTargets && exercise.subTargets.some(sub => categories.includes(sub));
        if (!matchesMainTarget && !matchesSubTarget) {
          return;
        }
      }
      
      if (!exerciseGroups[exercise.name]) {
        exerciseGroups[exercise.name] = {
          name: exercise.name,
          mainTarget: exercise.mainTarget,
          recentDate: routine.date,
          maxWeight: exercise.weight ?? null,
          totalVolume: exercise.sets * exercise.reps * (exercise.weight ?? 0),
          isFavorite: false,
        };
      } else {
        if (new Date(routine.date) > new Date(exerciseGroups[exercise.name].recentDate)) {
          exerciseGroups[exercise.name].recentDate = routine.date;
        }
        if (exercise.weight != null && (exerciseGroups[exercise.name].maxWeight == null || exercise.weight > exerciseGroups[exercise.name].maxWeight)) {
          exerciseGroups[exercise.name].maxWeight = exercise.weight;
        }
        exerciseGroups[exercise.name].totalVolume += exercise.sets * exercise.reps * (exercise.weight ?? 0);
      }
    });
  });

  const exercises = Object.values(exerciseGroups);

  useEffect(() => {
    console.log('=== 필터링 결과 ===');
    console.log('selectedFilter:', selectedFilter);
    console.log('categories:', categories);
    console.log('filteredRoutines:', filteredRoutines);
    console.log('exerciseGroups:', exerciseGroups);
    console.log('exercises:', exercises);
  }, [selectedFilter, filteredRoutines, exercises, categories, exerciseGroups]);

  const handleExerciseClick = (exerciseName) => {
    setSelectedExercise(exerciseName);
    setIsDetailModalOpen(true);
  };

  return (
    <div className="ml-20 p-8 min-h-screen">
      {/* 헤더 */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-2">
          <div>
            <h1 className="text-4xl font-bold mb-2 text-neon-green">My Workout Records</h1>
            <p className="text-neutral-50 text-lg">운동별 기록 및 성과</p>
          </div>
          {member && (
            <div className="text-right">
              <p className="text-neutral-400 text-sm">{member.name}님</p>
              <p className="text-neutral-500 text-xs">{member.target || 'BULK'}</p>
            </div>
          )}
        </div>
      </div>

      {/* 필터 버튼 */}
      <FilterButtons selectedFilter={selectedFilter} onFilterChange={setSelectedFilter} />

      {/* 운동 카드 그리드 */}
      <div className="grid grid-cols-3 gap-6 mt-8">
        {exercises.length > 0 ? (
          exercises.map((exercise, index) => (
            <div 
              key={index} 
              onClick={() => handleExerciseClick(exercise.name)}
              className="cursor-pointer"
            >
              <ExerciseCard exercise={exercise} />
            </div>
          ))
        ) : (
          <div className="col-span-3 text-center text-neutral-400 py-12">
            <p className="text-lg mb-2">{selectedFilter} 부위에 대한 운동 기록이 없습니다.</p>
            <p className="text-sm">전체 루틴: {historyRoutines.length}개</p>
            <p className="text-sm">완료된 루틴: {filteredRoutines.length}개</p>
            <p className="text-sm mt-4">다른 필터를 선택해보세요.</p>
          </div>
        )}
      </div>

      {/* 운동 상세 기록 모달 */}
      <ExerciseDetailModal
        exerciseName={selectedExercise}
        routines={historyRoutines}
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
      />
    </div>
  );
}
