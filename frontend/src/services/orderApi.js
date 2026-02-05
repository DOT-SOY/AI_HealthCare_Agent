/**
 * 주문/결제 API (jwtAxios 사용, JWT 인증·리프레시 자동 처리)
 */
import jwtAxios from '../util/jwtUtil';

/**
 * 장바구니 기준 주문 생성 (from-cart)
 * @param {object} body - { shipTo: { recipientName, recipientPhone, zipcode, address1, address2 }, buyer: { buyerName, buyerEmail, buyerPhone }, memo? }
 * @returns {Promise<{ orderNo, amount, orderName, orderId? }>}
 */
export const createOrderFromCart = async (body) => {
  const { data } = await jwtAxios.post('/orders/from-cart', body);
  return data;
};

/**
 * 결제 준비 (Toss 위젯용 데이터)
 * @param {string} orderNo - 주문번호
 * @returns {Promise<{ orderId, amount, orderName, clientKey, customerKey }>}
 *   - customerKey: 회원=memberId 문자열, 비회원=장바구니 UUID(guest_token). 토스 결제위젯 v2 widgets({ customerKey })용.
 */
export const preparePayment = async (orderNo) => {
  const { data } = await jwtAxios.post(`/orders/${encodeURIComponent(orderNo)}/pay/ready`);
  return data;
};

/**
 * Toss 결제 승인 (success 리다이렉트 후 호출)
 * @param {object} body - { paymentKey, orderId, amount }
 * @returns {Promise<{ orderId, orderStatus, amount, approvedAt }>}
 */
export const confirmTossPayment = async (body) => {
  const { data } = await jwtAxios.post('/payments/toss/confirm', body);
  return data;
};

/**
 * 회원 본인 주문 목록 조회 (JWT 인증 필수)
 * @param {object} params - { page?, page_size?, from_date?, to_date?, status? }
 * @returns {Promise<{ items: Array<{ orderNo, status, totalPayableAmount, createdAt, firstProductName, itemCount }>, page, page_size, total, pages, has_next, has_previous }>}
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

  const url = `/orders/me?${queryParams.toString()}`;
  const { data } = await jwtAxios.get(url);
  return data;
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
  const { data } = await jwtAxios.get(`/orders/${encodeURIComponent(orderNo)}`);
  return data;
};

/**
 * 회원 주문 배송지 수정
 * @param {string} orderNo - 주문번호
 * @param {object} body - { recipientName, recipientPhone, zipcode, address1, address2 }
 */
export const updateOrderShipTo = async (orderNo, body) => {
  const { data } = await jwtAxios.patch(`/orders/${encodeURIComponent(orderNo)}/ship-to`, body);
  return data;
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

  const { data } = await jwtAxios.get(`/admin/orders?${queryParams.toString()}`);
  return data;
};

/**
 * 관리자 주문 상세 조회 (ADMIN 권한, 전체 유저 주문)
 * @param {string} orderNo - 주문번호
 */
export const getAdminOrderDetail = async (orderNo) => {
  const { data } = await jwtAxios.get(`/admin/orders/${encodeURIComponent(orderNo)}`);
  return data;
};

/**
 * 관리자 주문 배송 상태 변경 (ADMIN 권한 필요)
 * @param {string} orderNo - 주문번호
 * @param {string} status - SHIPPED | DELIVERED | CANCELED
 */
export const updateOrderStatusAdmin = async (orderNo, status) => {
  const { data } = await jwtAxios.patch(`/admin/orders/${encodeURIComponent(orderNo)}/status`, { status });
  return data;
};
