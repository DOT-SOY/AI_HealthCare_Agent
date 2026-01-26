import { useState, createContext, useContext, useRef, useCallback } from 'react';
import BasicLayout from './BasicLayout';
import FloatingCartButton from '../cart/FloatingCartButton';
import CartDrawer from '../cart/CartDrawer';

// 장바구니 Context 생성
const CartContext = createContext(null);

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within ShopLayout');
  }
  return context;
};

const ShopLayout = ({ children }) => {
  // { items: Array, lastProcessedUpdateId: number }
  const [cartState, setCartState] = useState({ items: [], lastProcessedUpdateId: 0 });
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [animateButton, setAnimateButton] = useState(false);

  // StrictMode/중복실행 방지용 updateId
  const lastUpdateIdRef = useRef(0);

  // 장바구니에 아이템 추가 (UI 전용 더미 상태)
  const addToCart = useCallback((product, variant = null, qty = 1) => {
    const updateId = ++lastUpdateIdRef.current;

    setCartState((prev) => {
      // prev는 항상 {items, lastProcessedUpdateId} 형태로 유지
      if (prev.lastProcessedUpdateId === updateId) return prev;

      const items = Array.isArray(prev.items) ? prev.items : [];

      const existingIndex = items.findIndex(
        (item) =>
          item.product?.id === product?.id &&
          ((!item.variant && !variant) || item.variant?.id === variant?.id)
      );

      const addQty = Number(qty ?? 1);

      if (existingIndex >= 0) {
        const updated = items.map((it, i) =>
          i === existingIndex ? { ...it, qty: (it.qty ?? 0) + addQty } : it
        );
        return { items: updated, lastProcessedUpdateId: updateId };
      }

      return {
        items: [...items, { product, variant, qty: Math.max(1, addQty) }],
        lastProcessedUpdateId: updateId,
      };
    });

    // 담기 성공 시 버튼 애니메이션
    setAnimateButton(true);
    setTimeout(() => setAnimateButton(false), 600);
  }, []);

  // 장바구니 아이템 수량 업데이트 (index 기반)
  const updateQty = useCallback((index, newQty) => {
    const qty = Number(newQty);
    if (!Number.isFinite(qty) || qty < 1) return;

    setCartState((prev) => {
      const items = Array.isArray(prev.items) ? prev.items : [];
      if (index < 0 || index >= items.length) return prev;

      const updated = items.map((it, i) => (i === index ? { ...it, qty } : it));
      return { ...prev, items: updated };
    });
  }, []);

  // 장바구니 아이템 제거 (index 기반)
  const removeItem = useCallback((index) => {
    setCartState((prev) => {
      const items = Array.isArray(prev.items) ? prev.items : [];
      if (index < 0 || index >= items.length) return prev;

      return { ...prev, items: items.filter((_, i) => i !== index) };
    });
  }, []);

  const items = Array.isArray(cartState.items) ? cartState.items : [];
  const totalItemCount = items.reduce((sum, item) => sum + (Number(item?.qty) || 0), 0);

  const cartContextValue = {
    cartItems: items,
    addToCart,
    updateQty,
    removeItem,
    isDrawerOpen,
    openDrawer: () => setIsDrawerOpen(true),
    closeDrawer: () => setIsDrawerOpen(false),
    toggleDrawer: () => setIsDrawerOpen((v) => !v),
  };

  return (
    <CartContext.Provider value={cartContextValue}>
      <BasicLayout>
        <div className="bg-baseBg min-h-screen">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 lg:py-16">
            {children}
          </div>
        </div>

        <FloatingCartButton
          itemCount={totalItemCount}
          onClick={() => setIsDrawerOpen((v) => !v)}
          animate={animateButton}
        />

        <CartDrawer
          isOpen={isDrawerOpen}
          onClose={() => setIsDrawerOpen(false)}
          cartItems={items}
          onUpdateQty={updateQty}
          onRemoveItem={removeItem}
        />
      </BasicLayout>
    </CartContext.Provider>
  );
};

export default ShopLayout;
