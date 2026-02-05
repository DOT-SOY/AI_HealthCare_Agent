import { useState, useEffect, useMemo } from 'react';
import { routineApi } from '../../api/routineApi';
import FilterButtons from '../../components/record/FilterButtons';
import ExerciseCard from '../../components/record/ExerciseCard';
import ExerciseDetailModal from '../../components/record/ExerciseDetailModal';
import LoadingModal from '../../components/common/LoadingModal';

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
  const [latestRoutines, setLatestRoutines] = useState({});
  const [selectedFilter, setSelectedFilter] = useState('전체');
  const [selectedExercise, setSelectedExercise] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  // 초기 로딩: 각 운동별 최신 루틴만 가져오기
  useEffect(() => {
    const loadLatestRoutines = async () => {
      try {
        setLoading(true);
        const data = await routineApi.getLatestByExercise();
        setLatestRoutines(data || {});
      } catch (err) {
        console.error('최신 루틴 조회 실패:', err);
        setLatestRoutines({});
      } finally {
        setLoading(false);
      }
    };
    
    loadLatestRoutines();
  }, []);

  // categories 메모이제이션
  const categories = useMemo(() => {
    return bodyPartToCategory[selectedFilter] || [];
  }, [selectedFilter]);

  // 필터링된 운동 목록 메모이제이션
  const exercises = useMemo(() => {
    const exerciseList = Object.values(latestRoutines).map(routine => {
      if (!routine.exercises || routine.exercises.length === 0) {
        return null;
      }
      
      const exercise = routine.exercises[0]; // 최신 루틴의 첫 번째 운동
      const mainTarget = exercise.mainTarget;
      const subTargets = exercise.subTargets || [];
      
      // 필터링: 메인 타겟 또는 서브 타겟 확인
      if (selectedFilter !== '전체') {
        const matchesMainTarget = mainTarget && categories.includes(mainTarget);
        const matchesSubTarget = subTargets.some(sub => categories.includes(sub));
        if (!matchesMainTarget && !matchesSubTarget) {
          return null;
        }
      }
      
      // 통계 계산
      const maxWeight = exercise.weight ?? null;
      const totalVolume = exercise.sets * exercise.reps * (exercise.weight ?? 0);
      
      return {
        name: exercise.name,
        mainTarget: mainTarget,
        subTargets: subTargets,
        recentDate: routine.date,
        maxWeight: maxWeight,
        totalVolume: totalVolume,
        isFavorite: false,
      };
    }).filter(ex => ex !== null);
    
    return exerciseList;
  }, [latestRoutines, selectedFilter, categories]);

  const handleExerciseClick = (exerciseName) => {
    setSelectedExercise(exerciseName);
    setIsDetailModalOpen(true);
  };

  return (
    <div className="w-full">
      {/* 헤더 */}
      <header className="section-header-token">
        <h1 className="section-title">
          <span className="text-text-main">My Workout </span>
          <span className="text-primary-500">Records</span>
        </h1>
        <p className="section-desc">운동별 기록 및 성과</p>
      </header>

      {/* 필터 버튼 */}
      <FilterButtons selectedFilter={selectedFilter} onFilterChange={setSelectedFilter} />

      {/* 운동 카드 그리드 */}
      {loading ? (
        <LoadingModal isOpen={true} message="로딩 중..." />
      ) : exercises.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 mt-token-8">
          {exercises.map((exercise, index) => (
            <div
              key={`${exercise.name}-${index}`}
              onClick={() => handleExerciseClick(exercise.name)}
              className="cursor-pointer"
            >
              <ExerciseCard exercise={exercise} />
            </div>
          ))}
        </div>
      ) : (
        <div className="col-span-full text-center text-text-muted py-12">
          <p className="text-lg mb-2">{selectedFilter} 부위에 대한 운동 기록이 없습니다.</p>
          <p className="text-sm mt-4">다른 필터를 선택해보세요.</p>
        </div>
      )}

      {/* 운동 상세 기록 모달 */}
      <ExerciseDetailModal
        exerciseName={selectedExercise}
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
      />
    </div>
  );
}
