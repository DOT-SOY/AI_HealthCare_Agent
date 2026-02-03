/**
 * 주문/결제 API (jwtAxios 통일)
 */

import jwtAxios from '../util/jwtUtil';

/**
 * 장바구니 기준 주문 생성 (from-cart)
 * @param {object} body - { shipTo: { recipientName, recipientPhone, zipcode, address1, address2 }, buyer: { buyerName, buyerEmail, buyerPhone }, memo? }
 * @returns {Promise<{ orderNo, amount, orderName, orderId? }>}
 */
export const createOrderFromCart = async (body) => {
  const res = await jwtAxios.post('/orders/from-cart', body);
  return res.data;
};

/**
 * 결제 준비 (Toss 위젯용 데이터)
 * @param {string} orderNo - 주문번호
 * @returns {Promise<{ orderId, amount, orderName, clientKey, customerKey }>}
 */
export const preparePayment = async (orderNo) => {
  const res = await jwtAxios.post(`/orders/${encodeURIComponent(orderNo)}/pay/ready`);
  return res.data;
};

/**
 * Toss 결제 승인 (success 리다이렉트 후 호출)
 * @param {object} body - { paymentKey, orderId, amount }
 * @returns {Promise<{ orderId, orderStatus, amount, approvedAt }>}
 */
export const confirmTossPayment = async (body) => {
  const res = await jwtAxios.post('/payments/toss/confirm', body);
  return res.data;
};

/**
 * 회원 본인 주문 목록 조회 (JWT 인증 필수)
 * @param {object} params - { page?, page_size?, from_date?, to_date?, status? }
 */
export const getMyOrders = async (params = {}) => {
  const {
    page = 1,
    page_size = 20,
    from_date = null,
    to_date = null,
    status = null,
  } = params;

  const queryParams = new URLSearchParams({
    page: String(page),
    page_size: String(page_size),
  });
  if (from_date) queryParams.append('from_date', from_date);
  if (to_date) queryParams.append('to_date', to_date);
  if (status) queryParams.append('status', status);

  const res = await jwtAxios.get(`/orders/me?${queryParams.toString()}`);
  return res.data;
};

/**
 * 회원 주문 상세 조회
 * @param {string} orderNo - 주문번호
 */
export const getOrderDetail = async (orderNo) => {
  const res = await jwtAxios.get(`/orders/${encodeURIComponent(orderNo)}`);
  return res.data;
};

/**
 * 회원 주문 배송지 수정
 * @param {string} orderNo - 주문번호
 * @param {object} body - { recipientName, recipientPhone, zipcode, address1, address2 }
 */
export const updateOrderShipTo = async (orderNo, body) => {
  const res = await jwtAxios.patch(`/orders/${encodeURIComponent(orderNo)}/ship-to`, body);
  return res.data;
};

/**
 * 관리자 전체 주문 목록 조회 (ADMIN 권한 필요)
 * @param {object} params - { page?, page_size?, from_date?, to_date?, status? }
 * @returns {Promise<{ items, page, page_size, total, pages, has_next, has_previous }>}
 */
export const getAdminOrders = async (params = {}) => {
  const {
    page = 1,
    page_size = 20,
    from_date = null,
    to_date = null,
    status = null,
  } = params;

  const queryParams = new URLSearchParams({
    page: String(page),
    page_size: String(page_size),
  });
  if (from_date) queryParams.append('from_date', from_date);
  if (to_date) queryParams.append('to_date', to_date);
  if (status) queryParams.append('status', status);

  return await fetchAPI(`/admin/orders?${queryParams.toString()}`);
};

/**
 * 관리자 주문 상세 조회 (ADMIN 권한 필요, 배송 상태 관리용)
 * @param {string} orderNo - 주문번호
 */
export const getAdminOrderDetail = async (orderNo) => {
  return await fetchAPI(`/admin/orders/${encodeURIComponent(orderNo)}`);
};

/**
 * 관리자 주문 배송 상태 변경 (ADMIN 권한 필요)
 * @param {string} orderNo - 주문번호
 * @param {string} status - SHIPPED | DELIVERED | CANCELED
 */
export const updateOrderStatusAdmin = async (orderNo, status) => {
  return await fetchAPI(`/admin/orders/${encodeURIComponent(orderNo)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
};
