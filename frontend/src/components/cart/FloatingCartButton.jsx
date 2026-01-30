import { useEffect, useState } from 'react';

const FloatingCartButton = ({ itemCount, onClick, animate = false }) => {
  const [isAnimating, setIsAnimating] = useState(false);

  useEffect(() => {
    if (animate) {
      setIsAnimating(true);
      const timer = setTimeout(() => setIsAnimating(false), 600);
      return () => clearTimeout(timer);
    }
  }, [animate]);

  return (
    <button
      type="button"
      onClick={onClick}
      className={`fixed bottom-24 right-8 z-40 w-14 h-14 rounded-full flex items-center justify-center text-text-sub hover:text-primary-500 transition-colors duration-300 ${
        isAnimating ? 'animate-bounce scale-110' : ''
      }`}
      aria-label="장바구니"
    >
      <div className="relative">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="h-7 w-7"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
        </svg>
        {itemCount > 0 && (
          <span className="absolute -top-1.5 -right-1.5 bg-primary-500 text-bg-root text-xs font-bold rounded-full min-w-[1.25rem] h-5 px-1 flex items-center justify-center">
            {itemCount > 99 ? '99+' : itemCount}
          </span>
        )}
      </div>
    </button>
  );
};

export default FloatingCartButton;
