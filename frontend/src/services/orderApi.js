/**
 * 주문/결제 API
 */
import fetchAPI from './api';

/**
 * 장바구니 기준 주문 생성 (from-cart)
 * @param {object} body - { shipTo: { recipientName, recipientPhone, zipcode, address1, address2 }, buyer: { buyerName, buyerEmail, buyerPhone }, memo? }
 * @returns {Promise<{ orderNo, amount, orderName, orderId? }>}
 */
export const createOrderFromCart = async (body) => {
  return await fetchAPI('/orders/from-cart', {
    method: 'POST',
    body: JSON.stringify(body),
  });
};

/**
 * 결제 준비 (Toss 위젯용 데이터)
 * @param {string} orderNo - 주문번호
 * @returns {Promise<{ orderId, amount, orderName, clientKey, customerKey }>}
 *   - customerKey: 회원=memberId 문자열, 비회원=장바구니 UUID(guest_token). 토스 결제위젯 v2 widgets({ customerKey })용.
 */
export const preparePayment = async (orderNo) => {
  return await fetchAPI(`/orders/${encodeURIComponent(orderNo)}/pay/ready`, {
    method: 'POST',
  });
};

/**
 * Toss 결제 승인 (success 리다이렉트 후 호출)
 * @param {object} body - { paymentKey, orderId, amount }
 * @returns {Promise<{ orderId, orderStatus, amount, approvedAt }>}
 */
export const confirmTossPayment = async (body) => {
  return await fetchAPI('/payments/toss/confirm', {
    method: 'POST',
    body: JSON.stringify(body),
  });
};

/**
 * 회원 주문 상세 조회
 * @param {string} orderNo - 주문번호
 * @returns {Promise<{
 *   orderNo,
 *   status,
 *   totalItemAmount,
 *   shippingFee,
 *   totalPayableAmount,
 *   createdAt,
 *   buyer: { name, email, phone },
 *   shipTo: { recipientName, recipientPhone, zipcode, address1, address2 },
 *   items: Array<{ id, status, productName, variantOption, unitPrice, qty, lineAmount }>
 * }>}
 */
export const getOrderDetail = async (orderNo) => {
  return await fetchAPI(`/orders/${encodeURIComponent(orderNo)}`);
};
