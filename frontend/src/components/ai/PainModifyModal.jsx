import { useState } from 'react';
import { routineApi } from '../../api/routineApi';

export default function PainModifyModal({ open, onClose, date = '', painArea = '', routineTitle = '', replacements = [] }) {
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState(null);
  const [selections, setSelections] = useState({});

  if (!open) return null;

  const handleSelect = (exerciseId, value) => {
    setSelections((prev) => ({ ...prev, [exerciseId]: value || '' }));
  };

  const handleApply = async () => {
    setError(null);
    setApplying(true);
    try {
      const payload = {
        date: date || new Date().toISOString().split('T')[0],
        replacements: replacements.map((r) => ({
          exerciseId: r.exerciseId,
          selectedName: selections[r.exerciseId] || '',
        })),
      };
      await routineApi.applyPainModify(payload);
      window.dispatchEvent(new CustomEvent('routine-updated'));
      onClose?.();
    } catch (e) {
      setError(e.response?.data?.message || e.message || '적용에 실패했습니다.');
    } finally {
      setApplying(false);
    }
  };

  const hasChanges = replacements.some((r) => {
    const sel = selections[r.exerciseId];
    return sel != null && sel !== '' && sel !== r.exerciseName;
  });

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center">
      <div className="absolute inset-0 bg-black/60" onClick={onClose} aria-hidden />
      <div
        className="relative w-full max-w-lg max-h-[85vh] overflow-hidden rounded-token border border-border-default bg-bg-surface shadow-card flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border-default p-4">
          <h3 className="text-lg font-semibold text-text-main">
            {painArea} 부담 줄이기 · {routineTitle || date}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="text-text-muted hover:text-text-main transition-colors p-1"
            aria-label="닫기"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          <p className="text-sm text-text-muted">
            {painArea}에 부담이 적은 대체 운동이에요. 바꿀 운동만 선택하세요. 그대로 두려면 &quot;유지&quot;로 두세요.
          </p>
          {replacements.map((r) => (
            <div key={r.exerciseId} className="rounded-lg border border-border-default bg-bg-card/50 p-3">
              <p className="text-sm font-medium text-text-main mb-2">{r.exerciseName}</p>
              <select
                className="select-token w-full"
                value={selections[r.exerciseId] ?? ''}
                onChange={(e) => handleSelect(r.exerciseId, e.target.value)}
              >
                <option value="">(유지)</option>
                {(r.alternatives || []).map((alt) => (
                  <option key={alt} value={alt}>
                    {alt}
                  </option>
                ))}
              </select>
            </div>
          ))}
        </div>
        {error && (
          <p className="px-4 py-2 text-sm text-accent-secondary" role="alert">
            {error}
          </p>
        )}
        <div className="border-t border-border-default p-4 flex gap-2 justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg bg-gray-200 text-text-main hover:bg-gray-100 transition-colors"
          >
            취소
          </button>
          <button
            type="button"
            onClick={handleApply}
            disabled={applying || !hasChanges}
            className="px-4 py-2 rounded-lg font-medium text-bg-root bg-primary-500 hover:shadow-glow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {applying ? '적용 중...' : '선택 적용'}
          </button>
        </div>
      </div>
    </div>
  );
}
