import { useState, createContext, useContext, useCallback, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import BasicLayout from './BasicLayout';
import FloatingCartButton from '../cart/FloatingCartButton';
import CartDrawer from '../cart/CartDrawer';
import { addCartItem, getCart, updateCartItemQty, removeCartItem, clearCart } from '../../services/cartApi';

// 장바구니 Context 생성
const CartContext = createContext(null);

const emptyCart = { cartId: null, isGuest: true, items: [], totals: { itemCount: 0, totalQty: 0, totalPrice: 0 } };

const fallbackCartContext = {
  cartItems: [],
  addToCart: async () => {},
  updateQty: async () => {},
  removeItem: async () => {},
  resetCart: async () => {},
  isDrawerOpen: false,
  openDrawer: () => {},
  closeDrawer: () => {},
  toggleDrawer: () => {},
};

export const useCart = () => {
  const context = useContext(CartContext);
  return context ?? fallbackCartContext;
};

const ShopLayout = ({ children }) => {
  const location = useLocation();
  const navigate = useNavigate();
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

  const resetCart = useCallback(
    async (reason = 'unknown') => {
      try {
        await clearCart();
      } catch (e) {
        console.error('Failed to clear cart:', e);
      } finally {
        try {
          await refreshCart();
        } catch {
          // ignore
        }
      }
    },
    [refreshCart],
  );

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
      setTimeout(() => setAnimateButton(false), 1200);
    } catch (error) {
      console.error('Failed to add item to cart:', error);
      
      // 401 에러 시 로그인 페이지로 안내
      if (error.message?.includes('401') || error.message?.toLowerCase().includes('unauthorized')) {
        const shouldLogin = confirm('로그인이 필요합니다. 로그인 페이지로 이동하시겠습니까?');
        if (shouldLogin) {
          navigate('/member/login', { state: { from: location.pathname } });
        }
        return;
      }
      
      alert('장바구니에 담는 중 오류가 발생했습니다: ' + (error.message || '알 수 없는 오류'));
    }
  }, [refreshCart, navigate, location.pathname]);

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

  const isCheckoutPage = location.pathname.startsWith('/shop/checkout');

  const cartContextValue = {
    cartItems: items,
    addToCart,
    updateQty,
    removeItem,
    resetCart,
    isDrawerOpen,
    openDrawer: () => setIsDrawerOpen(true),
    closeDrawer: () => setIsDrawerOpen(false),
    toggleDrawer: () => setIsDrawerOpen((v) => !v),
  };

  return (
    <CartContext.Provider value={cartContextValue}>
      <BasicLayout containerClassName="page-container">
        {children}

        {!isCheckoutPage && (
          <>
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
          </>
        )}
      </BasicLayout>
    </CartContext.Provider>
  );
};

export default ShopLayout;
