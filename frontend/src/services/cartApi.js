import jwtAxios from '../util/jwtUtil';

export const getCart = async () => {
  const res = await jwtAxios.get('/cart');
  return res.data;
};

export const addCartItem = async (variantId, productId, qty) => {
  const body = { qty };
  if (variantId != null) {
    body.variantId = variantId;
  } else if (productId != null) {
    body.productId = productId;
  }
  const res = await jwtAxios.post('/cart/items', body);
  return res.data;
};

export const updateCartItemQty = async (itemId, qty) => {
  const res = await jwtAxios.patch(`/cart/items/${itemId}`, { qty });
  return res.data;
};

export const removeCartItem = async (itemId) => {
  const res = await jwtAxios.delete(`/cart/items/${itemId}`);
  return res.data;
};

/**
 * 장바구니 비우기
 */
export const clearCart = async () => {
  const res = await jwtAxios.delete('/cart');
  return res.data;
};

export const mergeCart = async () => {
  const res = await jwtAxios.post('/cart/merge');
  return res.data;
};
