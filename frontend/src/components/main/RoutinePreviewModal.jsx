import { useNavigate } from 'react-router-dom';

export default function RoutinePreviewModal({ isOpen, onClose, routineData, date }) {
  const navigate = useNavigate();

  if (!isOpen) return null;

  const formatDate = (date) => {
    if (!date) return '';
    const d = new Date(date);
    const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${weekdays[d.getDay()]})`;
  };

  const formatDateKey = (d) => {
    const dateObj = d instanceof Date ? d : new Date(d);
    const y = dateObj.getFullYear();
    const m = String(dateObj.getMonth() + 1).padStart(2, '0');
    const day = String(dateObj.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  const handleViewDetails = () => {
    if (date) {
      const dateStr = formatDateKey(date);
      navigate(`/routine/list?date=${dateStr}`);
    } else {
      navigate('/routine/list');
    }
    onClose();
  };

  const exercises = routineData?.exercises || [];
  const completedCount = exercises.filter((ex) => ex.completed).length;
  const totalCount = exercises.length;

  return (
    <div
      className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50"
      onClick={onClose}
    >
      <div
        className="bg-bg-card rounded-token p-6 w-full max-w-md border border-border-default"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold text-text-main">루틴 정보</h2>
          <button
            onClick={onClose}
            className="text-text-muted hover:text-text-main transition-colors"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="mb-4">
          <p className="text-text-sub text-sm">{formatDate(date)}</p>
        </div>

        {routineData ? (
          <div className="space-y-4">
            {/* 루틴 제목 */}
            <div>
              <h3 className="text-lg font-semibold text-text-main">{routineData.title || '루틴'}</h3>
              {routineData.summary && (
                <p className="text-text-sub text-sm mt-1">{routineData.summary}</p>
              )}
            </div>

            {/* 진행 상황 */}
            <div className="bg-bg-surface rounded-token p-4">
              <div className="flex justify-between items-center mb-2">
                <span className="text-text-sub">진행 상황</span>
                <span className="text-text-main font-bold">
                  {completedCount} / {totalCount}
                </span>
              </div>
              <div className="w-full bg-gray-100 rounded-full h-2">
                <div
                  className="bg-primary-500 h-2 rounded-full"
                  style={{ width: `${totalCount > 0 ? (completedCount / totalCount) * 100 : 0}%` }}
                />
              </div>
            </div>

            {/* 운동 목록 */}
            {exercises.length > 0 ? (
              <div className="space-y-2">
                <p className="text-text-sub text-sm font-medium">운동 목록</p>
                <div className="max-h-40 overflow-y-auto space-y-1">
                  {exercises.slice(0, 5).map((exercise, index) => (
                    <div key={index} className="flex items-center justify-between text-sm">
                      <span className={`${exercise.completed ? 'line-through text-text-muted' : 'text-text-main'}`}>
                        {exercise.name}
                      </span>
                      {exercise.completed && (
                        <span className="text-primary-500 text-xs">완료</span>
                      )}
                    </div>
                  ))}
                  {exercises.length > 5 && (
                    <p className="text-text-muted text-xs">외 {exercises.length - 5}개 운동...</p>
                  )}
                </div>
              </div>
            ) : (
              <p className="text-text-muted text-sm">등록된 운동이 없습니다.</p>
            )}
          </div>
        ) : (
          <div className="text-center py-8">
            <p className="text-text-muted">해당 날짜의 루틴이 없습니다.</p>
          </div>
        )}

        <div className="mt-6 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-2 rounded-token font-medium bg-gray-100 text-text-main hover:bg-gray-200 transition-colors"
          >
            닫기
          </button>
          <button
            onClick={handleViewDetails}
            className="flex-1 py-2 rounded-token font-medium bg-primary-500 text-bg-root hover:opacity-90 transition-colors"
          >
            자세히 보기
          </button>
        </div>
      </div>
    </div>
  );
}

