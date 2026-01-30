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
import Card from '../../components/common/Card';
import Button from '../../components/common/Button';

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
        <div className="text-lg text-text-main">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-primary-400">{error}</div>
        <Link to="/shop/list" className="ml-4 text-primary-500 hover:underline">
          목록으로 돌아가기
        </Link>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-text-main">상품을 찾을 수 없습니다.</div>
        <Link to="/shop/list" className="ml-4 text-primary-500 hover:underline">
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
        <Link to="/shop/list" className="text-primary-500 hover:underline">
          ← 목록으로 돌아가기
        </Link>
        {isAdmin && (
          <Button type="button" variant="primary" onClick={() => navigate(`/shop/admin/edit/${id}`)}>
            상품 수정
          </Button>
        )}
      </div>

      <Card className="overflow-hidden">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 p-8">
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
              <div className="w-full aspect-square bg-bg-surface rounded-lg flex items-center justify-center">
                <span className="text-text-muted">이미지 없음</span>
              </div>
            )}

            {product.images && product.images.length > 1 && (
              <div className="flex gap-2 overflow-x-auto">
                {product.images.map((image, index) => (
                  <button
                    key={image.uuid}
                    onClick={() => setSelectedImageIndex(index)}
                    className={`flex-shrink-0 w-20 h-20 rounded-lg overflow-hidden border-2 ${
                      selectedImageIndex === index ? 'border-primary-500' : 'border-border-default'
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

          <div>
            <h1 className="text-3xl font-bold text-text-main mb-4">{product.name}</h1>

            <div className="mb-6">
              <span
                className={`inline-block px-3 py-1 rounded text-sm ${
                  product.status === 'ACTIVE'
                    ? 'bg-primary-500/20 text-primary-400'
                    : 'bg-bg-surface text-text-muted'
                }`}
              >
                {product.status === 'ACTIVE' ? '판매중' : '품절'}
              </span>
            </div>

            <div className="mb-6">
              <div className="text-4xl font-bold text-primary-500 mb-2">
                {displayPrice != null ? displayPrice.toLocaleString() : '-'}원
              </div>
              {hasVariants && selectedVariant && (
                <p className="text-sm text-text-sub">선택된 옵션: {selectedVariant.optionText}</p>
              )}
            </div>

            <div className="mb-6">
              <h2 className="text-xl font-semibold text-text-main mb-2">상품 설명</h2>
              <p className="text-text-sub whitespace-pre-wrap">{product.description}</p>
            </div>

            {hasVariants && (
              <div className="mb-6">
                <h2 className="text-xl font-semibold text-text-main mb-3">옵션 (변형)</h2>
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
                              ? 'bg-primary-500 text-bg-root border-primary-500'
                              : 'bg-bg-card text-text-main border-border-default hover:border-primary-500'
                          }`}
                        >
                          {label}
                        </button>
                      );
                    })}
                </div>
              </div>
            )}

            <div className="mb-6 border-t border-border-default pt-6">
              <h2 className="text-xl font-semibold text-text-main mb-3">수량 선택</h2>
              <div className="flex items-center gap-4 mb-4">
                <QtyStepper
                  value={qty}
                  onChange={setQty}
                  disabled={product.status !== 'ACTIVE'}
                  buttonClassName="border-border-default bg-bg-card text-text-main hover:bg-bg-surface disabled:opacity-50"
                  valueClassName="text-text-main"
                />
              </div>
              <Button
                type="button"
                variant="primary"
                size="lg"
                className="w-full"
                onClick={handleAddToCart}
                disabled={product.status !== 'ACTIVE' || (hasVariants && !selectedVariant)}
              >
                장바구니에 담기
              </Button>
            </div>

            <div className="border-t border-border-default pt-6">
              <div className="grid grid-cols-2 gap-4 text-sm text-text-sub">
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
      </Card>

      <Card className="mt-8 overflow-hidden p-6">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-semibold text-text-main">상품 리뷰</h2>
            {product.reviewSummary != null && product.reviewSummary.count > 0 && (
              <span className="text-text-sub text-sm">
                평점 {Number(product.reviewSummary.average_rating ?? product.reviewSummary.averageRating).toFixed(1)} (리뷰 {product.reviewSummary.count}개)
              </span>
            )}
          </div>
          {reviewsData.total !== undefined && product.reviewSummary == null && (
            <span className="text-text-muted text-sm">총 {reviewsData.total}개</span>
          )}
          {isLoggedIn && (
            <Button
              type="button"
              variant={product.canReview === false ? 'ghost' : 'primary'}
              size="sm"
              onClick={() => product.canReview !== false && setReviewFormOpen((prev) => !prev)}
              disabled={product.canReview === false}
              className={product.canReview === false ? 'opacity-50 cursor-not-allowed' : ''}
            >
              {product.canReview === false ? '구매 후 작성 가능' : (reviewFormOpen ? '취소' : '리뷰 작성')}
            </Button>
          )}
        </div>

        {reviewFormOpen && (
          <form onSubmit={handleSubmitReview} className="mb-6 p-4 bg-bg-surface rounded-lg">
            <div className="mb-3">
              <label className="block text-sm font-medium text-text-main mb-1">평점</label>
              <select
                value={reviewForm.rating}
                onChange={(e) => setReviewForm((prev) => ({ ...prev, rating: Number(e.target.value) }))}
                className="border border-border-default rounded px-3 py-2 w-full max-w-[120px] bg-bg-card text-text-main"
              >
                {[1, 2, 3, 4, 5].map((n) => (
                  <option key={n} value={n}>{n}점</option>
                ))}
              </select>
            </div>
            <div className="mb-3">
              <label className="block text-sm font-medium text-text-main mb-1">내용 (선택)</label>
              <textarea
                value={reviewForm.content}
                onChange={(e) => setReviewForm((prev) => ({ ...prev, content: e.target.value }))}
                rows={3}
                className="border border-border-default rounded px-3 py-2 w-full bg-bg-card text-text-main placeholder-text-muted"
                placeholder="리뷰를 입력하세요"
              />
            </div>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              등록
            </Button>
          </form>
        )}

        {!isLoggedIn && (
          <p className="text-text-muted text-sm mb-4">로그인 후 리뷰를 작성할 수 있습니다.</p>
        )}

        {reviewsLoading ? (
          <div className="py-8 text-center text-text-muted">리뷰 로딩 중...</div>
        ) : reviewsData.items.length === 0 ? (
          <div className="py-8 text-center text-text-muted">아직 리뷰가 없습니다.</div>
        ) : (
          <>
            <ul className="divide-y divide-border-default">
              {reviewsData.items.map((review) => (
                <li key={review.id} className="py-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-primary-500" aria-hidden>
                          {'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}
                        </span>
                        <span className="text-sm font-medium text-text-main">{review.displayName ?? '회원'}</span>
                        <span className="text-xs text-text-muted">
                          {review.created_at ? new Date(review.created_at).toLocaleDateString('ko-KR') : ''}
                        </span>
                      </div>
                      {editingReviewId === review.id ? (
                        <form onSubmit={(e) => handleUpdateReview(e, review.id)} className="mt-2">
                          <select
                            value={editReviewForm.rating}
                            onChange={(e) => setEditReviewForm((prev) => ({ ...prev, rating: Number(e.target.value) }))}
                            className="border border-border-default rounded px-2 py-1 text-sm mb-2 bg-bg-card text-text-main"
                          >
                            {[1, 2, 3, 4, 5].map((n) => (
                              <option key={n} value={n}>{n}점</option>
                            ))}
                          </select>
                          <textarea
                            value={editReviewForm.content}
                            onChange={(e) => setEditReviewForm((prev) => ({ ...prev, content: e.target.value }))}
                            rows={2}
                            className="border border-border-default rounded px-2 py-1 w-full text-sm block mb-2 bg-bg-card text-text-main"
                          />
                          <div className="flex gap-2">
                            <button type="submit" disabled={submitting} className="text-sm text-primary-500 hover:underline">저장</button>
                            <button type="button" onClick={() => setEditingReviewId(null)} className="text-sm text-text-muted hover:underline">취소</button>
                          </div>
                        </form>
                      ) : (
                        <p className="text-text-sub text-sm whitespace-pre-wrap">{review.content || '(내용 없음)'}</p>
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
                          className="text-xs text-primary-500 hover:underline"
                        >
                          수정
                        </button>
                        <button type="button" onClick={() => handleDeleteReview(review.id)} className="text-xs text-primary-400 hover:underline">
                          삭제
                        </button>
                      </div>
                    )}
                  </div>
                  {review.replies && review.replies.length > 0 && (
                    <ul className="ml-4 mt-2 pl-4 border-l-2 border-border-default space-y-2">
                      {review.replies.map((reply) => (
                        <li key={reply.id} className="text-sm">
                          <span className="font-medium text-text-sub">{reply.author_display_name ?? '관리자'}</span>
                          <span className="text-text-muted text-xs ml-2">
                            {reply.created_at ? new Date(reply.created_at).toLocaleDateString('ko-KR') : ''}
                          </span>
                          {isAdmin && (
                            <button
                              type="button"
                              onClick={() => handleDeleteReply(review.id, reply.id)}
                              className="ml-2 text-xs text-primary-400 hover:underline"
                            >
                              삭제
                            </button>
                          )}
                          <p className="text-text-sub mt-0.5">{reply.content}</p>
                        </li>
                      ))}
                    </ul>
                  )}
                  {isAdmin && (
                    <form onSubmit={(e) => handleSubmitReply(e, review.id)} className="ml-4 mt-2 flex gap-2 items-end">
                      <input
                        type="text"
                        value={replyFormByReviewId[review.id] ?? ''}
                        onChange={(e) => setReplyFormByReviewId((prev) => ({ ...prev, [review.id]: e.target.value }))}
                        placeholder="대댓글 입력..."
                        className="flex-1 border border-border-default rounded px-3 py-1.5 text-sm bg-bg-card text-text-main placeholder-text-muted"
                      />
                      <Button type="submit" variant="ghost" size="sm" disabled={submitting}>
                        등록
                      </Button>
                    </form>
                  )}
                </li>
              ))}
            </ul>
            {reviewsData.pages > 1 && (
              <div className="flex justify-center gap-2 mt-4">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={reviewPage <= 1}
                  onClick={() => setReviewPage((p) => Math.max(1, p - 1))}
                >
                  이전
                </Button>
                <span className="py-1 text-sm text-text-sub">
                  {reviewPage} / {reviewsData.pages}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={reviewPage >= reviewsData.pages}
                  onClick={() => setReviewPage((p) => Math.min(reviewsData.pages, p + 1))}
                >
                  다음
                </Button>
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  );
};

export default ProductDetail;

