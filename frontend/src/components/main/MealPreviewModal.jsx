import { useNavigate } from 'react-router-dom';

export default function MealPreviewModal({ isOpen, onClose, mealData, date }) {
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
      navigate(`/meal/dashboard?date=${dateStr}`);
    } else {
      navigate('/meal/dashboard');
    }
    onClose();
  };

  const totalCalories = mealData?.calories?.current || 0;
  const goalCalories = mealData?.calories?.goal || 0;
  const breakfast = mealData?.breakfast || { meals: [], totalCalories: 0 };
  const lunch = mealData?.lunch || { meals: [], totalCalories: 0 };
  const dinner = mealData?.dinner || { meals: [], totalCalories: 0 };

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
          <h2 className="text-xl font-bold text-text-main">식사 정보</h2>
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

        {mealData ? (
          <div className="space-y-4">
            {/* 총 칼로리 */}
            <div className="bg-bg-surface rounded-token p-4">
              <div className="flex justify-between items-center">
                <span className="text-text-sub">총 칼로리</span>
                <span className="text-text-main font-bold">
                  {totalCalories} / {goalCalories} kcal
                </span>
              </div>
              <div className="mt-2 w-full bg-gray-100 rounded-full h-2">
                <div
                  className="bg-primary-500 h-2 rounded-full"
                  style={{ width: `${Math.min((totalCalories / goalCalories) * 100, 100)}%` }}
                />
              </div>
            </div>

            {/* 식사별 정보 */}
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-text-sub">아침</span>
                <span className="text-text-main">{breakfast.totalCalories} kcal</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-text-sub">점심</span>
                <span className="text-text-main">{lunch.totalCalories} kcal</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span className="text-text-sub">저녁</span>
                <span className="text-text-main">{dinner.totalCalories} kcal</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="text-center py-8">
            <p className="text-text-muted">해당 날짜의 식사 정보가 없습니다.</p>
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

