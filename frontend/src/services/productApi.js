import jwtAxios from '../util/jwtUtil';

/**
 * 상품 관련 API
 */

const prefix = '/products';

/**
 * 상품 리스트 조회 (페이징, 검색, 필터링)
 */
export const getProductList = async (params = {}) => {
  const {
    page = 1,
    page_size = 20,
    keyword = '',
    categoryId = null,
    minPrice = null,
    maxPrice = null,
    status = null,
    sortBy = 'createdAt',
    direction = 'DESC',
    signal, // AbortController signal
  } = params;

  const queryParams = new URLSearchParams({
    page: page.toString(),
    page_size: page_size.toString(),
    sortBy,
    direction,
  });

  if (keyword) queryParams.append('keyword', keyword);
  if (categoryId) queryParams.append('categoryId', categoryId.toString());
  if (minPrice) queryParams.append('minPrice', minPrice.toString());
  if (maxPrice) queryParams.append('maxPrice', maxPrice.toString());
  if (status) queryParams.append('status', status);

  const requestUrl = `${prefix}?${queryParams.toString()}`;
  const res = await jwtAxios.get(requestUrl, {
    signal, // AbortController signal 지원
  });
  return res.data;
};

/**
 * 상품 단건 조회
 */
export const getProduct = async (id, signal) => {
  const res = await jwtAxios.get(`${prefix}/${id}`, {
    signal, // AbortController signal 지원
  });
  return res.data;
};

/**
 * 상품 생성 (관리자)
 */
export const createProduct = async (productData) => {
  const res = await jwtAxios.post(prefix, productData);
  return res.data;
};

/**
 * 상품 수정 (관리자)
 */
export const updateProduct = async (id, productData) => {
  const res = await jwtAxios.patch(`${prefix}/${id}`, productData);
  return res.data;
};

/**
 * 상품 삭제 (관리자)
 */
export const deleteProduct = async (id) => {
  const res = await jwtAxios.delete(`${prefix}/${id}`);
  // 204 No Content 응답 처리
  return res.status === 204 ? null : res.data;
};

