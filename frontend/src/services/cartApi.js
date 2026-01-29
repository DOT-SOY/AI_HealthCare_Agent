/**
 * 카트 API 호출 함수
 */

import fetchAPI from './api';

/**
 * 장바구니 조회
 */
export const getCart = async () => {
  return await fetchAPI('/cart', {
    method: 'GET',
  });
};

/**
 * 장바구니에 아이템 추가
 * @param {number|null} variantId - 상품 변형 ID (옵션이 있는 경우)
 * @param {number|null} productId - 상품 ID (옵션이 없는 경우)
 * @param {number} qty - 수량
 */
export const addCartItem = async (variantId, productId, qty) => {
  const body = { qty };
  if (variantId != null) {
    body.variantId = variantId;
  } else if (productId != null) {
    body.productId = productId;
  }
  
  return await fetchAPI('/cart/items', {
    method: 'POST',
    body: JSON.stringify(body),
  });
};

/**
 * 장바구니 아이템 수량 변경
 * @param {number} itemId - 장바구니 아이템 ID
 * @param {number} qty - 수량
 */
export const updateCartItemQty = async (itemId, qty) => {
  return await fetchAPI(`/cart/items/${itemId}`, {
    method: 'PATCH',
    body: JSON.stringify({
      qty,
    }),
  });
};

/**
 * 장바구니 아이템 제거
 * @param {number} itemId - 장바구니 아이템 ID
 */
export const removeCartItem = async (itemId) => {
  return await fetchAPI(`/cart/items/${itemId}`, {
    method: 'DELETE',
  });
};

/**
 * 장바구니 비우기
 */
export const clearCart = async () => {
  return await fetchAPI('/cart', {
    method: 'DELETE',
  });
};

/**
 * 게스트 장바구니를 회원 장바구니로 병합
 */
export const mergeCart = async () => {
  return await fetchAPI('/cart/merge', {
    method: 'POST',
  });
};
