import jwtAxios from '../util/jwtUtil';

/**
 * 상품 리뷰 API
 */

/**
 * 상품별 리뷰 목록 조회 (페이징)
 */
export const getProductReviews = async (productId, params = {}) => {
  const { page = 1, page_size = 10, signal } = params;
  const queryParams = new URLSearchParams({ page: String(page), page_size: String(page_size) });
  const res = await jwtAxios.get(`/products/${productId}/reviews?${queryParams.toString()}`, { signal });
  return res.data;
};

/**
 * 리뷰 작성
 */
export const createReview = async (productId, { rating, content, imageFilePaths }) => {
  const payload = { rating, content };
  if (imageFilePaths && imageFilePaths.length > 0) {
    payload.imageFilePaths = imageFilePaths;
  }
  const res = await jwtAxios.post(`/products/${productId}/reviews`, payload);
  return res.data;
};

/**
 * 리뷰 수정
 */
export const updateReview = async (reviewId, { rating, content, imageFilePaths }) => {
  const payload = { rating, content };
  if (imageFilePaths && imageFilePaths.length > 0) {
    payload.imageFilePaths = imageFilePaths;
  } else if (imageFilePaths) {
    // 빈 배열도 명시적으로 전달해 모든 이미지 제거
    payload.imageFilePaths = [];
  }
  const res = await jwtAxios.patch(`/reviews/${reviewId}`, payload);
  return res.data;
};

/**
 * 리뷰 삭제
 */
export const deleteReview = async (reviewId) => {
  await jwtAxios.delete(`/reviews/${reviewId}`);
};

/**
 * 대댓글 작성 (관리자)
 */
export const createReply = async (reviewId, { content }) => {
  const res = await jwtAxios.post(`/reviews/${reviewId}/replies`, { content });
  return res.data;
};

/**
 * 대댓글 삭제 (관리자)
 */
export const deleteReply = async (reviewId, replyId) => {
  await jwtAxios.delete(`/reviews/${reviewId}/replies/${replyId}`);
};
