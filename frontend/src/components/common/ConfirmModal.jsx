export default function ConfirmModal({
  isOpen,
  title = '확인',
  message = '',
  confirmText = '확인',
  cancelText = '취소',
  onConfirm,
  onCancel,
  confirmVariant = 'primary', // 'primary' | 'danger'
  closeOnBackdrop = true,
  showCloseButton = true,
}) {
  if (!isOpen) return null;

  const confirmClass =
    confirmVariant === 'danger'
      ? 'bg-accent-secondary text-white hover:opacity-90'
      : 'bg-primary-500 text-bg-root hover:opacity-90';

  return (
    <div
      className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50"
      onClick={() => {
        if (!closeOnBackdrop) return;
        onCancel && onCancel();
      }}
      aria-hidden
    >
      <div
        className="bg-bg-card rounded-token p-6 w-full max-w-md border border-border-default shadow-card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="flex items-center justify-between gap-3 mb-3">
          <h3 className="text-lg font-bold text-text-main">{title}</h3>
          {showCloseButton && (
            <button
              type="button"
              onClick={() => onCancel && onCancel()}
              className="text-text-muted hover:text-text-main transition-colors"
              aria-label="닫기"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>

        <p className="text-sm text-text-sub whitespace-pre-wrap leading-6">{message}</p>

        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={() => onCancel && onCancel()}
            className="flex-1 py-2 rounded-token font-medium bg-bg-root text-text-main hover:bg-gray-100 transition-colors border border-border-default"
          >
            {cancelText}
          </button>
          <button
            type="button"
            onClick={() => onConfirm && onConfirm()}
            className={`flex-1 py-2 rounded-token font-medium transition-colors ${confirmClass}`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}


