export default function ExerciseCard({ exercise }) {
  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
  };

  const formatNumber = (num) => {
    return num.toLocaleString('ko-KR');
  };

  const getCategoryName = (category) => {
    const categoryMap = {
      'BACK': '등',
      'CHEST': '가슴',
      'SHOULDER': '어깨',
      'ARM': '팔',
      'CORE': '코어',
      'ABS': '복근',
      'GLUTE': '둔근',
      'THIGH': '허벅지',
      'CALF': '종아리',
    };
    return categoryMap[category] || category;
  };

  return (
    <div className="bg-neutral-800 rounded-lg p-6 relative hover:bg-neutral-700 transition-colors cursor-pointer">
      {/* 카테고리 태그 */}
      <div className="flex gap-2 mb-3">
        <span className="text-xs text-neutral-400 bg-neutral-700 px-2 py-1 rounded">
          {getCategoryName(exercise.mainTarget)}
        </span>
      </div>

      {/* 운동명 */}
      <h3 className="text-2xl font-bold text-neutral-50 mb-4">{exercise.name}</h3>

      {/* 통계 정보 */}
      <div className="space-y-2 text-sm">
        <div className="flex justify-between">
          <span className="text-neutral-400">최근 수행</span>
          <span className="font-medium" style={{ color: '#88ce02' }}>{formatDate(exercise.recentDate)}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-neutral-400">최고 중량(1RM)</span>
          <span className="font-medium" style={{ color: '#88ce02' }}>{exercise.maxWeight != null ? `${exercise.maxWeight}kg` : '-'}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-neutral-400">총 볼륨</span>
          <span className="font-medium" style={{ color: '#88ce02' }}>{formatNumber(exercise.totalVolume)} kg</span>
        </div>
      </div>
    </div>
  );
}
