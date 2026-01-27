import { useState, createContext, useContext, useCallback, useEffect } from 'react';
import BasicLayout from './BasicLayout';
import FloatingCartButton from '../cart/FloatingCartButton';
import CartDrawer from '../cart/CartDrawer';
import { addCartItem, getCart, updateCartItemQty, removeCartItem } from '../../services/cartApi';

// 장바구니 Context 생성
const CartContext = createContext(null);

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within ShopLayout');
  }
  return context;
};

const emptyCart = { cartId: null, isGuest: true, items: [], totals: { itemCount: 0, totalQty: 0, totalPrice: 0 } };

const ShopLayout = ({ children }) => {
  const [cartState, setCartState] = useState(emptyCart);
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [animateButton, setAnimateButton] = useState(false);

  const refreshCart = useCallback(async () => {
    try {
      const data = await getCart();
      setCartState(data && (data.items != null) ? data : emptyCart);
    } catch (_) {
      setCartState(emptyCart);
    }
  }, []);

  useEffect(() => {
    refreshCart();
  }, [refreshCart]);

  const addToCart = useCallback(async (product, variant = null, qty = 1) => {
    let targetVariant = variant;
    if (!targetVariant && product.variants?.length > 0) {
      const active = product.variants.filter(v => v.active);
      if (active.length > 0) targetVariant = active[0];
    }
    const addQty = Number(qty ?? 1);

    try {
      if (targetVariant?.id) {
        await addCartItem(targetVariant.id, null, addQty);
      } else {
        await addCartItem(null, product.id, addQty);
      }
      await refreshCart();
      setAnimateButton(true);
      setTimeout(() => setAnimateButton(false), 600);
    } catch (error) {
      console.error('Failed to add item to cart:', error);
      alert('장바구니에 담는 중 오류가 발생했습니다: ' + (error.message || '알 수 없는 오류'));
    }
  }, [refreshCart]);

  // itemId 기준 수량 변경 — API 호출 후 GET /api/cart 재조회로 동기화
  const updateQty = useCallback(async (itemId, newQty) => {
    const qty = Number(newQty);
    if (!Number.isFinite(qty) || qty < 1) return;
    try {
      await updateCartItemQty(itemId, qty);
      await refreshCart();
    } catch (e) {
      console.error('Failed to update cart item:', e);
    }
  }, [refreshCart]);

  // itemId 기준 삭제 — variantId 사용 금지, API 호출 후 GET /api/cart 재조회로 동기화
  const removeItem = useCallback(async (itemId) => {
    try {
      await removeCartItem(itemId);
      await refreshCart();
    } catch (e) {
      console.error('Failed to remove cart item:', e);
    }
  }, [refreshCart]);

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
          totals={cartState.totals}
          onUpdateQty={updateQty}
          onRemoveItem={removeItem}
        />
      </BasicLayout>
    </CartContext.Provider>
  );
};

export default ShopLayout;
