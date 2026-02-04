function LoadingModal({ isOpen, message = "로딩 중..." }) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-[9999]">
      <div className="bg-neutral-900 rounded-lg p-8 text-center min-w-[200px]">
        {/* 로딩 스피너 */}
        <div className="flex justify-center mb-4">
          <div className="spinner-token" />
        </div>
        <p className="text-neutral-50 text-lg">{message}</p>
      </div>
    </div>
  );
}

export default LoadingModal;