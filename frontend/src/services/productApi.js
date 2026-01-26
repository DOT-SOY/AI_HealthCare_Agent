import fetchAPI from './api';

/**
 * 상품 관련 API
 */

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

  return fetchAPI(`/v1/products?${queryParams.toString()}`);
};

/**
 * 상품 단건 조회
 */
export const getProduct = async (id) => {
  return fetchAPI(`/v1/products/${id}`);
};

/**
 * 상품 생성 (관리자)
 */
export const createProduct = async (productData) => {
  return fetchAPI('/v1/products', {
    method: 'POST',
    body: JSON.stringify(productData),
  });
};

/**
 * 상품 수정 (관리자)
 */
export const updateProduct = async (id, productData) => {
  return fetchAPI(`/v1/products/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(productData),
  });
};

/**
 * 상품 삭제 (관리자)
 */
export const deleteProduct = async (id) => {
  return fetchAPI(`/v1/products/${id}`, {
    method: 'DELETE',
  });
};

