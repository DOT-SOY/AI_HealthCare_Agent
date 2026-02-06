import { useEffect, useState, useRef, useMemo } from 'react';

// 파티클 버스트용: 버튼 중심으로부터 무작위 방향으로 날아가는 입자 좌표 생성
const generateParticles = (count = 8) =>
  Array.from({ length: count }, (_, i) => {
    const angle = (360 / count) * i + (Math.random() * 30 - 15);
    const dist = 28 + Math.random() * 18;
    const rad = (angle * Math.PI) / 180;
    return {
      id: i,
      px: `${Math.cos(rad) * dist}px`,
      py: `${Math.sin(rad) * dist}px`,
      delay: Math.random() * 0.1,
      size: 4 + Math.random() * 3,
    };
  });

const FloatingCartButton = ({ itemCount, onClick, animate = false }) => {
  const [isAnimating, setIsAnimating] = useState(false);
  const [badgePop, setBadgePop] = useState(false);
  const [showToast, setShowToast] = useState(false);
  const [showPing, setShowPing] = useState(false);
  const [showParticles, setShowParticles] = useState(false);
  const [showQtyFloat, setShowQtyFloat] = useState(false);
  const prevCountRef = useRef(itemCount);
  const addedQtyRef = useRef(0);

  const particles = useMemo(() => generateParticles(8), [showParticles]);

  // 1계층: 아이콘 Jiggle + Glow / 3계층: 토스트 알림 / 추가 동적 효과들
  useEffect(() => {
    if (animate) {
      setIsAnimating(true);
      setShowToast(true);
      setShowPing(true);
      setShowParticles(true);
      setShowQtyFloat(true);

      const jiggleTimer = setTimeout(() => setIsAnimating(false), 600);
      const toastTimer = setTimeout(() => setShowToast(false), 1200);
      const pingTimer = setTimeout(() => setShowPing(false), 700);
      const particleTimer = setTimeout(() => setShowParticles(false), 700);
      const qtyFloatTimer = setTimeout(() => setShowQtyFloat(false), 900);

      return () => {
        clearTimeout(jiggleTimer);
        clearTimeout(toastTimer);
        clearTimeout(pingTimer);
        clearTimeout(particleTimer);
        clearTimeout(qtyFloatTimer);
      };
    }
  }, [animate]);

  // 2계층: 뱃지 Pop 애니메이션 (itemCount 변화 감지)
  useEffect(() => {
    if (itemCount > 0 && itemCount !== prevCountRef.current) {
      addedQtyRef.current = itemCount - prevCountRef.current;
      setBadgePop(true);
      const timer = setTimeout(() => setBadgePop(false), 400);
      prevCountRef.current = itemCount;
      return () => clearTimeout(timer);
    }
    prevCountRef.current = itemCount;
  }, [itemCount]);

  return (
    <div className="fixed bottom-24 right-8 z-40" style={{ overflow: 'visible' }}>
      {/* 3계층: 미니 토스트 */}
      {showToast && (
        <div className="absolute bottom-full right-0 mb-2 pointer-events-none whitespace-nowrap animate-toast-up">
          <div className="bg-bg-surface text-text-main text-sm font-semibold px-3 py-1.5 rounded-token shadow-glow-sm border border-primary-500/30">
            장바구니에 담았습니다
          </div>
        </div>
      )}

      {/* +N 플로팅 인디케이터 */}
      {showQtyFloat && addedQtyRef.current > 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <span className="animate-float-up-fade text-primary-500 font-bold text-lg drop-shadow-md">
            +{addedQtyRef.current}
          </span>
        </div>
      )}

      {/* 소나 핑 링 (이중) */}
      {showPing && (
        <>
          <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
            <div className="w-14 h-14 rounded-full border-2 border-primary-500/60 animate-ping-ring" />
          </div>
          <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
            <div
              className="w-14 h-14 rounded-full border-2 border-primary-500/40 animate-ping-ring"
              style={{ animationDelay: '0.15s' }}
            />
          </div>
        </>
      )}

      {/* 파티클 버스트 */}
      {showParticles && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
          {particles.map((p) => (
            <div
              key={p.id}
              className="absolute rounded-full bg-primary-500 animate-particle-burst"
              style={{
                width: p.size,
                height: p.size,
                '--px': p.px,
                '--py': p.py,
                animationDelay: `${p.delay}s`,
              }}
            />
          ))}
        </div>
      )}

      <button
        type="button"
        onClick={onClick}
        className={`w-14 h-14 rounded-full flex items-center justify-center text-text-sub hover:text-primary-500 transition-all duration-300 ${
          isAnimating ? 'animate-cart-jiggle text-primary-500 shadow-glow' : ''
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
            <span
              className={`absolute -top-1.5 -right-1.5 bg-primary-500 text-bg-root text-xs font-bold rounded-full min-w-[1.25rem] h-5 px-1 flex items-center justify-center ${
                badgePop ? 'animate-badge-pop' : ''
              }`}
            >
              {itemCount > 99 ? '99+' : itemCount}
            </span>
          )}
        </div>
      </button>
    </div>
  );
};

export default FloatingCartButton;
