import { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { getProduct } from '../../services/productApi';
import {
  getProductReviews,
  createReview,
  updateReview,
  deleteReview,
  createReply,
  deleteReply,
} from '../../services/reviewApi';
import { useCart } from '../../components/layout/ShopLayout';
import QtyStepper from '../../components/cart/QtyStepper';

const ProductDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);
  const isAdmin = !!loginState?.roleNames?.includes('ADMIN');
  const { addToCart } = useCart();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [selectedVariant, setSelectedVariant] = useState(null);
  const [qty, setQty] = useState(1);
  // 리뷰
  const [reviewsData, setReviewsData] = useState({ items: [], page: 1, page_size: 10, total: 0, pages: 0 });
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewPage, setReviewPage] = useState(1);
  const [reviewForm, setReviewForm] = useState({ rating: 5, content: '' });
  const [reviewFormOpen, setReviewFormOpen] = useState(false);
  const [editingReviewId, setEditingReviewId] = useState(null);
  const [editReviewForm, setEditReviewForm] = useState({ rating: 5, content: '' });
  const [replyFormByReviewId, setReplyFormByReviewId] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const isLoggedIn = !!loginState?.email;
  const currentMemberId = loginState?.memberId ?? loginState?.id;

  const loadReviews = useCallback(async () => {
    if (!id) return;
    setReviewsLoading(true);
    try {
      const data = await getProductReviews(id, { page: reviewPage, page_size: 10 });
      setReviewsData({
        items: data.items ?? [],
        page: data.page ?? 1,
        page_size: data.page_size ?? 10,
        total: data.total ?? 0,
        pages: data.pages ?? 0,
      });
    } catch (err) {
      console.error('Failed to load reviews:', err);
    } finally {
      setReviewsLoading(false);
    }
  }, [id, reviewPage]);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (!id || submitting) return;
    setSubmitting(true);
    try {
      await createReview(id, { rating: reviewForm.rating, content: reviewForm.content || null });
      setReviewForm({ rating: 5, content: '' });
      setReviewFormOpen(false);
      loadReviews();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '리뷰 작성에 실패했습니다.';
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateReview = async (e, reviewId) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      await updateReview(reviewId, { rating: editReviewForm.rating, content: editReviewForm.content || null });
      setEditingReviewId(null);
      loadReviews();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '리뷰 수정에 실패했습니다.';
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReview = async (reviewId) => {
    if (!window.confirm('리뷰를 삭제하시겠습니까?')) return;
    try {
      await deleteReview(reviewId);
      loadReviews();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '리뷰 삭제에 실패했습니다.';
      alert(msg);
    }
  };

  const handleSubmitReply = async (e, reviewId) => {
    e.preventDefault();
    const content = replyFormByReviewId[reviewId]?.trim();
    if (!content || submitting) return;
    setSubmitting(true);
    try {
      await createReply(reviewId, { content });
      setReplyFormByReviewId((prev) => ({ ...prev, [reviewId]: '' }));
      loadReviews();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '대댓글 작성에 실패했습니다.';
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteReply = async (reviewId, replyId) => {
    if (!window.confirm('대댓글을 삭제하시겠습니까?')) return;
    try {
      await deleteReply(reviewId, replyId);
      loadReviews();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '대댓글 삭제에 실패했습니다.';
      alert(msg);
    }
  };

  useEffect(() => {
    const abortController = new AbortController();
    
    const loadProduct = async () => {
      try {
        setLoading(true);
        setError(null);
        setSelectedVariant(null);
        const data = await getProduct(id, abortController.signal); // AbortController signal 전달
        
        // 요청이 취소되었는지 확인
        if (abortController.signal.aborted) return;
        
        setProduct(data);
        if (data.images && data.images.length > 0) {
          const primaryIndex = data.images.findIndex(img => img.primaryImage);
          if (primaryIndex >= 0) {
            setSelectedImageIndex(primaryIndex);
          }
        }
      } catch (err) {
        // AbortError는 무시 (요청 취소)
        if (err.name === 'AbortError') return;
        
        // 요청이 취소되었는지 확인
        if (abortController.signal.aborted) return;
        
        setError(err.message || '상품 정보를 불러오는데 실패했습니다.');
        console.error('Failed to load product:', err);
      } finally {
        // 요청이 취소되었는지 확인
        if (!abortController.signal.aborted) {
          setLoading(false);
        }
      }
    };
    
    loadProduct();
    
    // cleanup: 컴포넌트 언마운트 또는 id 변경 시 이전 요청 취소
    return () => {
      abortController.abort();
    };
  }, [id]);

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-lg">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-red-500">{error}</div>
        <Link to="/shop/list" className="ml-4 text-blue-500 hover:underline">
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div>상품을 찾을 수 없습니다.</div>
        <Link to="/shop/list" className="ml-4 text-blue-500 hover:underline">
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  const selectedImage = product.images && product.images.length > 0 
    ? product.images[selectedImageIndex] 
    : null;

  const displayPrice = selectedVariant
    ? (selectedVariant.price != null ? Number(selectedVariant.price) : product.basePrice)
    : product.basePrice;

  const hasVariants = product.variants && product.variants.length > 0;

  const handleAddToCart = () => {
    addToCart(product, selectedVariant, qty);
  };

  return (
    <div className="w-full">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
        <Link 
          to="/shop/list" 
          className="text-blue-500 hover:underline"
        >
          ← 목록으로 돌아가기
        </Link>
        {isAdmin && (
          <button
            type="button"
            onClick={() => navigate(`/shop/admin/edit/${id}`)}
            className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition font-medium"
          >
            상품 수정
          </button>
        )}
      </div>

      <div className="bg-white rounded-lg shadow-lg overflow-hidden">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 p-8">
          {/* 이미지 섹션 */}
          <div>
            {selectedImage ? (
              <div className="mb-4">
                <img
                  src={selectedImage.url}
                  alt={product.name}
                  className="w-full aspect-square object-cover rounded-lg"
                  onError={(e) => {
                    e.target.src = 'https://via.placeholder.com/600x600?text=No+Image';
                  }}
                />
              </div>
            ) : (
              <div className="w-full aspect-square bg-gray-100 rounded-lg flex items-center justify-center">
                <span className="text-gray-400">이미지 없음</span>
              </div>
            )}

            {/* 썸네일 목록 */}
            {product.images && product.images.length > 1 && (
              <div className="flex gap-2 overflow-x-auto">
                {product.images.map((image, index) => (
                  <button
                    key={image.uuid}
                    onClick={() => setSelectedImageIndex(index)}
                    className={`flex-shrink-0 w-20 h-20 rounded-lg overflow-hidden border-2 ${
                      selectedImageIndex === index 
                        ? 'border-blue-500' 
                        : 'border-gray-200'
                    }`}
                  >
                    <img
                      src={image.url}
                      alt={`${product.name} ${index + 1}`}
                      className="w-full h-full object-cover"
                      onError={(e) => {
                        e.target.src = 'https://via.placeholder.com/80x80?text=No+Image';
                      }}
                    />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 상품 정보 섹션 */}
          <div>
            <h1 className="text-3xl font-bold mb-4">{product.name}</h1>
            
            <div className="mb-6">
              <span className={`inline-block px-3 py-1 rounded text-sm ${
                product.status === 'ACTIVE' 
                  ? 'bg-green-100 text-green-800' 
                  : 'bg-gray-100 text-gray-800'
              }`}>
                {product.status === 'ACTIVE' ? '판매중' : '품절'}
              </span>
            </div>

            <div className="mb-6">
              <div className="text-4xl font-bold text-blue-600 mb-2">
                {displayPrice != null ? displayPrice.toLocaleString() : '-'}원
              </div>
              {hasVariants && selectedVariant && (
                <p className="text-sm text-gray-600">
                  선택된 옵션: {selectedVariant.optionText}
                </p>
              )}
            </div>

            <div className="mb-6">
              <h2 className="text-xl font-semibold mb-2">상품 설명</h2>
              <p className="text-gray-700 whitespace-pre-wrap">{product.description}</p>
            </div>

            {hasVariants && (
              <div className="mb-6">
                <h2 className="text-xl font-semibold mb-3">옵션 (변형)</h2>
                <div className="flex flex-wrap gap-2">
                  {product.variants
                    .filter((v) => v.active)
                    .map((v) => {
                      const label = v.optionText || `옵션 #${v.id}`;
                      const isSelected = selectedVariant?.id === v.id;
                      return (
                        <button
                          key={v.id}
                          type="button"
                          onClick={() => setSelectedVariant(isSelected ? null : v)}
                          className={`px-4 py-2 rounded-lg font-medium transition border-2 ${
                            isSelected
                              ? 'bg-blue-500 text-white border-blue-500 hover:bg-blue-600'
                              : 'bg-white text-gray-700 border-gray-300 hover:border-blue-400 hover:bg-blue-50'
                          }`}
                        >
                          {label}
                        </button>
                      );
                    })}
                </div>
              </div>
            )}

            <div className="mb-6 border-t pt-6">
              <h2 className="text-xl font-semibold mb-3">수량 선택</h2>
              <div className="flex items-center gap-4 mb-4">
                <QtyStepper
                  value={qty}
                  onChange={setQty}
                  disabled={product.status !== 'ACTIVE'}
                />
              </div>
              <button
                type="button"
                onClick={handleAddToCart}
                disabled={product.status !== 'ACTIVE' || (hasVariants && !selectedVariant)}
                className="w-full py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition font-medium text-lg"
              >
                장바구니에 담기
              </button>
            </div>

            <div className="border-t pt-6">
              <div className="grid grid-cols-2 gap-4 text-sm text-gray-600">
                <div>
                  <span className="font-semibold">상품 ID:</span> {product.id}
                </div>
                <div>
                  <span className="font-semibold">등록일:</span>{' '}
                  {product.createdAt 
                    ? new Date(product.createdAt).toLocaleDateString('ko-KR')
                    : '-'}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 상품 리뷰 섹션 */}
      <div className="mt-8 bg-white rounded-lg shadow-lg overflow-hidden p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-semibold">상품 리뷰</h2>
            {product.reviewSummary != null && product.reviewSummary.count > 0 && (
              <span className="text-gray-600 text-sm">
                평점 {Number(product.reviewSummary.average_rating ?? product.reviewSummary.averageRating).toFixed(1)} (리뷰 {product.reviewSummary.count}개)
              </span>
            )}
          </div>
          {reviewsData.total !== undefined && product.reviewSummary == null && (
            <span className="text-gray-500 text-sm">총 {reviewsData.total}개</span>
          )}
          {isLoggedIn && (
            <button
              type="button"
              onClick={() => product.canReview !== false && setReviewFormOpen((prev) => !prev)}
              disabled={product.canReview === false}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition ${
                product.canReview === false
                  ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                  : 'bg-blue-500 text-white hover:bg-blue-600'
              }`}
            >
              {product.canReview === false ? '구매 후 작성 가능' : (reviewFormOpen ? '취소' : '리뷰 작성')}
            </button>
          )}
        </div>

        {reviewFormOpen && (
          <form onSubmit={handleSubmitReview} className="mb-6 p-4 bg-gray-50 rounded-lg">
            <div className="mb-3">
              <label className="block text-sm font-medium text-gray-700 mb-1">평점</label>
              <select
                value={reviewForm.rating}
                onChange={(e) => setReviewForm((prev) => ({ ...prev, rating: Number(e.target.value) }))}
                className="border border-gray-300 rounded px-3 py-2 w-full max-w-[120px]"
              >
                {[1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>{n}점</option>
                ))}
              </select>
            </div>
            <div className="mb-3">
              <label className="block text-sm font-medium text-gray-700 mb-1">내용 (선택)</label>
              <textarea
                value={reviewForm.content}
                onChange={(e) => setReviewForm((prev) => ({ ...prev, content: e.target.value }))}
                rows={3}
                className="border border-gray-300 rounded px-3 py-2 w-full"
                placeholder="리뷰를 입력하세요"
              />
            </div>
            <button type="submit" disabled={submitting} className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50 text-sm font-medium">
              등록
            </button>
          </form>
        )}

        {!isLoggedIn && (
          <p className="text-gray-500 text-sm mb-4">로그인 후 리뷰를 작성할 수 있습니다.</p>
        )}

        {reviewsLoading ? (
          <div className="py-8 text-center text-gray-500">리뷰 로딩 중...</div>
        ) : reviewsData.items.length === 0 ? (
          <div className="py-8 text-center text-gray-500">아직 리뷰가 없습니다.</div>
        ) : (
          <>
            <ul className="divide-y divide-gray-200">
              {reviewsData.items.map((review) => (
                <li key={review.id} className="py-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-yellow-500" aria-hidden>
                          {'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}
                        </span>
                        <span className="text-sm font-medium text-gray-700">{review.displayName ?? '회원'}</span>
                        <span className="text-xs text-gray-400">
                          {review.created_at ? new Date(review.created_at).toLocaleDateString('ko-KR') : ''}
                        </span>
                      </div>
                      {editingReviewId === review.id ? (
                        <form
                          onSubmit={(e) => handleUpdateReview(e, review.id)}
                          className="mt-2"
                        >
                          <select
                            value={editReviewForm.rating}
                            onChange={(e) => setEditReviewForm((prev) => ({ ...prev, rating: Number(e.target.value) }))}
                            className="border border-gray-300 rounded px-2 py-1 text-sm mb-2"
                          >
                            {[1, 2, 3, 4, 5].map((n) => (
                              <option key={n} value={n}>{n}점</option>
                            ))}
                          </select>
                          <textarea
                            value={editReviewForm.content}
                            onChange={(e) => setEditReviewForm((prev) => ({ ...prev, content: e.target.value }))}
                            rows={2}
                            className="border border-gray-300 rounded px-2 py-1 w-full text-sm block mb-2"
                          />
                          <div className="flex gap-2">
                            <button type="submit" disabled={submitting} className="text-sm text-blue-600 hover:underline">저장</button>
                            <button type="button" onClick={() => setEditingReviewId(null)} className="text-sm text-gray-500 hover:underline">취소</button>
                          </div>
                        </form>
                      ) : (
                        <p className="text-gray-700 text-sm whitespace-pre-wrap">{review.content || '(내용 없음)'}</p>
                      )}
                    </div>
                    {!editingReviewId && currentMemberId != null && Number(review.memberId) === Number(currentMemberId) && (
                      <div className="flex gap-2 flex-shrink-0">
                        <button
                          type="button"
                          onClick={() => {
                            setEditingReviewId(review.id);
                            setEditReviewForm({ rating: review.rating, content: review.content ?? '' });
                          }}
                          className="text-xs text-blue-600 hover:underline"
                        >
                          수정
                        </button>
                        <button type="button" onClick={() => handleDeleteReview(review.id)} className="text-xs text-red-600 hover:underline">
                          삭제
                        </button>
                      </div>
                    )}
                  </div>
                  {/* 대댓글 목록 */}
                  {review.replies && review.replies.length > 0 && (
                    <ul className="ml-4 mt-2 pl-4 border-l-2 border-gray-100 space-y-2">
                      {review.replies.map((reply) => (
                        <li key={reply.id} className="text-sm">
                          <span className="font-medium text-gray-600">{reply.author_display_name ?? '관리자'}</span>
                          <span className="text-gray-400 text-xs ml-2">
                            {reply.created_at ? new Date(reply.created_at).toLocaleDateString('ko-KR') : ''}
                          </span>
                          {isAdmin && (
                            <button
                              type="button"
                              onClick={() => handleDeleteReply(review.id, reply.id)}
                              className="ml-2 text-xs text-red-600 hover:underline"
                            >
                              삭제
                            </button>
                          )}
                          <p className="text-gray-700 mt-0.5">{reply.content}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                  {/* 관리자 대댓글 작성 */}
                  {isAdmin && (
                    <form
                      onSubmit={(e) => handleSubmitReply(e, review.id)}
                      className="ml-4 mt-2 flex gap-2 items-end"
                    >
                      <input
                        type="text"
                        value={replyFormByReviewId[review.id] ?? ''}
                        onChange={(e) => setReplyFormByReviewId((prev) => ({ ...prev, [review.id]: e.target.value }))}
                        placeholder="대댓글 입력..."
                        className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"
                      />
                      <button type="submit" disabled={submitting} className="px-3 py-1.5 bg-gray-600 text-white rounded text-sm hover:bg-gray-700 disabled:opacity-50">
                        등록
                      </button>
                    </form>
                  )}
                </li>
              ))}
            </ul>
            {reviewsData.pages > 1 && (
              <div className="flex justify-center gap-2 mt-4">
                <button
                  type="button"
                  disabled={reviewPage <= 1}
                  onClick={() => setReviewPage((p) => Math.max(1, p - 1))}
                  className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50 text-sm"
                >
                  이전
                </button>
                <span className="py-1 text-sm text-gray-600">
                  {reviewPage} / {reviewsData.pages}
                </span>
                <button
                  type="button"
                  disabled={reviewPage >= reviewsData.pages}
                  onClick={() => setReviewPage((p) => Math.min(reviewsData.pages, p + 1))}
                  className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50 text-sm"
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default ProductDetail;

