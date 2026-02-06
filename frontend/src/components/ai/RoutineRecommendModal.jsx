import { useState } from 'react';
import { routineApi } from '../../api/routineApi';

export default function RoutineRecommendModal({ open, onClose, splitType = 2, days = [] }) {
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

  if (!open) return null;

  const handleCreate = async () => {
    setError(null);
    setCreating(true);
    try {
      const today = new Date().toISOString().split('T')[0];
      await routineApi.createFromRecommendation({
        startDate: today,
        splitType: splitType || 2,
        days: days.map((d) => ({
          dayIndex: d.dayIndex,
          label: d.label,
          exercises: (d.exercises || [])
            .map((ex) => ({
              exercise_name: ex.exercise_name ?? ex.name,
              body_part: ex.body_part,
            }))
            .filter((ex) => ex.exercise_name),
        })),
      });
      window.dispatchEvent(new CustomEvent('routine-updated'));
      onClose?.();
    } catch (e) {
      setError(e.response?.data?.message || e.message || '루틴 저장에 실패했습니다.');
    } finally {
      setCreating(false);
    }
  };

  const title = splitType === 5 ? '5분할 루틴' : splitType === 4 ? '4분할 루틴' : '2분할 루틴';

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60" onClick={onClose} aria-hidden />
      <div
        className="relative w-full max-w-lg max-h-[85vh] overflow-hidden rounded-xl border border-neutral-700 bg-neutral-900 shadow-2xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-neutral-700 p-4">
          <h3 className="text-lg font-semibold text-neutral-50">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-50 transition-colors p-1"
            aria-label="닫기"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {days.map((day) => (
            <div key={day.dayIndex} className="rounded-lg border border-neutral-700 bg-neutral-800/50 p-4">
              <h4 className="text-base font-medium text-neutral-50 mb-2">
                {day.label || `${day.dayIndex}일차`}
              </h4>
              <ul className="space-y-1 text-sm text-neutral-300">
                {(day.exercises || []).map((ex, idx) => (
                  <li key={idx}>
                    {ex.exercise_name ?? ex.name}
                    {ex.body_part ? ` (${ex.body_part})` : ''}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        {error && (
          <p className="px-4 py-2 text-sm text-red-400" role="alert">
            {error}
          </p>
        )}
        <div className="border-t border-neutral-700 p-4 flex gap-2 justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-neutral-700 text-neutral-200 hover:bg-neutral-600 transition-colors"
          >
            닫기
          </button>
          <button
            type="button"
            onClick={handleCreate}
            disabled={creating || !days.length}
            className="px-4 py-2 rounded-lg font-medium text-neutral-950 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            style={{ backgroundColor: '#88ce02' }}
          >
            {creating ? '저장 중...' : '루틴 생성하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
