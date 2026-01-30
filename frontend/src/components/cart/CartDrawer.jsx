import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import QtyStepper from './QtyStepper';
import Button from '../common/Button';

const CART_ANIMATION_MS = 320;

const CartDrawer = ({ isOpen, onClose, cartItems, totals, onUpdateQty, onRemoveItem }) => {
  const navigate = useNavigate();
  const [isExiting, setIsExiting] = useState(false);
  const [entered, setEntered] = useState(false);

  const totalQty = totals?.totalQty ?? cartItems.reduce((sum, item) => sum + (item.qty ?? 0), 0);
  const totalPrice = totals?.totalPrice ?? cartItems.reduce((sum, item) => {
    const p = Number(item.price ?? 0);
    return sum + p * (item.qty ?? 0);
  }, 0);

  const handleClose = useCallback(() => {
    setIsExiting(true);
  }, []);

  useEffect(() => {
    if (!isOpen) {
      setEntered(false);
      setIsExiting(false);
      return;
    }
    setEntered(false);
    const raf = requestAnimationFrame(() => {
      requestAnimationFrame(() => setEntered(true));
    });
    return () => cancelAnimationFrame(raf);
  }, [isOpen]);

  useEffect(() => {
    if (!isExiting) return;
    const t = setTimeout(() => {
      onClose();
      setIsExiting(false);
    }, CART_ANIMATION_MS);
    return () => clearTimeout(t);
  }, [isExiting, onClose]);

  const visible = isOpen || isExiting;

  useEffect(() => {
    if (!visible) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') handleClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [visible, handleClose]);
  const panelOpen = entered && !isExiting;

  if (!visible) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex md:items-center md:justify-center items-end justify-center pointer-events-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cart-title"
    >
      {/* Backdrop: blur + fade */}
      <div
        className={`absolute inset-0 bg-black/60 backdrop-blur-sm transition-opacity duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] ${
          panelOpen ? 'opacity-100' : 'opacity-0'
        }`}
        onClick={handleClose}
        aria-hidden
      />

      {/* Panel: 데스크톱 중앙 모달(scale+fade) / 모바일 바텀시트(slide-up) */}
      <div
        className={`relative z-10 w-full max-w-md max-h-[85vh] md:max-h-[90vh] flex flex-col bg-bg-surface text-text-main rounded-t-2xl md:rounded-2xl shadow-2xl border border-border-default md:border overflow-hidden transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] ${
          panelOpen
            ? 'opacity-100 translate-y-0 scale-100'
            : 'opacity-0 translate-y-full md:translate-y-0 scale-95 md:scale-95'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 모바일: 위쪽 드래그 핸들 */}
        <div className="md:hidden flex justify-center pt-2 pb-1">
          <div className="w-10 h-1 rounded-full bg-gray-300/60" aria-hidden />
        </div>

        <div className="flex items-center justify-between px-4 pb-3 md:pt-4 md:pb-4 border-b border-border-default">
          <h2 id="cart-title" className="text-xl font-bold text-text-main">
            장바구니
          </h2>
          <button
            type="button"
            onClick={handleClose}
            className="p-2 -mr-2 hover:bg-bg-card rounded-xl transition-colors text-text-main"
            aria-label="장바구니 닫기"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto overscroll-contain p-4 min-h-0">
          {cartItems.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-text-muted">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16 mb-4 opacity-60" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
              <p className="text-text-muted">장바구니가 비어있습니다</p>
            </div>
          ) : (
            <div className="space-y-4">
              {cartItems.map((item) => {
                const price = Number(item.price ?? 0);
                const imageUrl = item.primaryImageUrl ?? 'https://via.placeholder.com/100x100?text=No+Image';
                const itemId = item.itemId;
                if (!itemId) return null;
                return (
                  <div key={itemId} className="flex gap-4 p-4 border border-border-default rounded-xl bg-bg-card">
                    <img
                      src={imageUrl}
                      alt={item.productName ?? ''}
                      className="w-20 h-20 object-cover rounded-lg flex-shrink-0"
                      onError={(e) => { e.target.src = 'https://via.placeholder.com/100x100?text=No+Image'; }}
                    />
                    <div className="flex-1 min-w-0">
                      <h3 className="font-semibold text-text-main mb-1 truncate">{item.productName ?? ''}</h3>
                      {item.optionSummary && (
                        <p className="text-sm text-text-sub mb-2">옵션: {item.optionSummary}</p>
                      )}
                      <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div>
                          <p className="text-lg font-bold text-primary-500 font-emphasis">{price.toLocaleString()}원</p>
                          <QtyStepper
                            value={item.qty ?? 1}
                            onChange={(newQty) => onUpdateQty(itemId, newQty)}
                            buttonClassName="border-border-default bg-bg-card text-text-main hover:bg-bg-surface disabled:opacity-50 rounded-lg"
                            valueClassName="text-text-main"
                          />
                        </div>
                        <button
                          type="button"
                          onClick={() => onRemoveItem(itemId)}
                          className="p-2 text-primary-400 hover:bg-bg-surface rounded-lg transition-colors"
                          aria-label="삭제"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {cartItems.length > 0 && (
          <div className="border-t border-border-default p-4 pb-6 space-y-4 bg-bg-surface flex-shrink-0">
            <div className="flex justify-between text-lg text-text-main">
              <span className="font-semibold">총 수량</span>
              <span className="font-bold">{totalQty}개</span>
            </div>
            <div className="flex justify-between text-xl text-text-main">
              <span className="font-semibold">총 금액</span>
              <span className="font-bold text-primary-500 font-emphasis">{Number(totalPrice).toLocaleString()}원</span>
            </div>
            <Button
              type="button"
              variant="primary"
              size="lg"
              className="w-full"
              onClick={() => {
                handleClose();
                setTimeout(() => navigate('/shop/checkout'), CART_ANIMATION_MS);
              }}
            >
              주문하기
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};

export default CartDrawer;
