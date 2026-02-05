import jwtAxios from '../util/jwtUtil';

const prefix = '/products';

export const getProductList = async (params = {}) => {
  const {
    page = 1,
    page_size = 20,
    keyword = '',
    searchType = 'all',
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
  if (searchType) queryParams.append('searchType', searchType);
  if (categoryId) queryParams.append('categoryId', categoryId.toString());
  if (minPrice) queryParams.append('minPrice', minPrice.toString());
  if (maxPrice) queryParams.append('maxPrice', maxPrice.toString());
  if (status) queryParams.append('status', status);

  const requestUrl = `${prefix}?${queryParams.toString()}`;
  const res = await jwtAxios.get(requestUrl, { signal });
  return res.data;
};

export const getProduct = async (id, signal) => {
  const res = await jwtAxios.get(`${prefix}/${id}`, { signal });
  return res.data;
};

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

export const deleteProduct = async (id) => {
  const res = await jwtAxios.delete(`${prefix}/${id}`);
  return res.status === 204 ? null : res.data;
};

