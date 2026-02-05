import { useState, useEffect, useCallback, useMemo } from 'react';
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
import LoadingModal from '../../components/common/LoadingModal';
import { uploadFiles } from '../../services/fileApi';

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
  const [reviewsData, setReviewsData] = useState({ items: [], page: 1, page_size: 10, total: 0, pages: 0 });
  const [reviewsLoading, setReviewsLoading] = useState(false);
  const [reviewPage, setReviewPage] = useState(1);
  const [reviewForm, setReviewForm] = useState({ rating: 5, content: '' });
  const [reviewFormOpen, setReviewFormOpen] = useState(false);
  const [reviewImages, setReviewImages] = useState([]);
  const [uploadingReviews, setUploadingReviews] = useState(false);
  const [editingReviewId, setEditingReviewId] = useState(null);
  const [editReviewForm, setEditReviewForm] = useState({ rating: 5, content: '' });
  const [editReviewImagesById, setEditReviewImagesById] = useState({});
  const [replyFormByReviewId, setReplyFormByReviewId] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const isLoggedIn = !!loginState?.email || loginState?.id != null;
  const currentMemberId = loginState?.id ?? null;

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

  // 상품의 최신 리뷰 상태(review_status)만 다시 가져오기 위한 헬퍼
  const refreshProductReviewStatus = useCallback(async () => {
    if (!id) return;
    try {
      const data = await getProduct(id); // 별도 AbortController 없이 단순 조회
      setProduct(data);
    } catch (err) {
      console.error('Failed to refresh product review status:', err);
    }
  }, [id]);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (!id || submitting || uploadingReviews) return;
    if (reviewImages.length > 10) {
      alert('리뷰 이미지는 최대 10개까지 업로드할 수 있습니다.');
      return;
    }

    setSubmitting(true);
    try {
      let imageFilePaths = [];

      if (reviewImages.length > 0) {
        setUploadingReviews(true);
        const uploadResults = await uploadFiles(reviewImages, 'reviews');
        imageFilePaths = uploadResults
          .map((res) => res.filePath)
          .filter((path) => !!path);
      }

      await createReview(id, {
        rating: reviewForm.rating,
        content: reviewForm.content || null,
        imageFilePaths,
      });
      setReviewForm({ rating: 5, content: '' });
      setReviewImages([]);
      setReviewFormOpen(false);
      loadReviews();
      // 리뷰 작성 후 상품의 review_status도 최신 상태로 갱신
      refreshProductReviewStatus();
    } catch (err) {
      const msg = err.response?.data?.message ?? err.message ?? '리뷰 작성에 실패했습니다.';
      alert(msg);
    } finally {
      setUploadingReviews(false);
      setSubmitting(false);
    }
  };

  const handleUpdateReview = async (e, reviewId) => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const editState = editReviewImagesById[reviewId];
      let imageFilePaths;

      if (editState) {
        // 기존 이미지 중 유지할 것들
        const existingPaths =
          editState.existing?.map((img) => img.filePath).filter((p) => !!p) ?? [];

        let newPaths = [];
        if (editState.files && editState.files.length > 0) {
          const uploadResults = await uploadFiles(editState.files, 'reviews');
          newPaths = uploadResults
            .map((res) => res.filePath)
            .filter((path) => !!path);
        }

        imageFilePaths = [...existingPaths, ...newPaths];
      } else {
        // editState가 없으면 현재 리뷰의 이미지를 그대로 유지
        const currentReview = reviewsData.items.find((r) => r.id === reviewId);
        imageFilePaths = currentReview?.images?.map((img) => img.filePath).filter((p) => !!p) ?? [];
      }

      // 항상 imageFilePaths를 전달 (빈 배열도 포함하여 명시적으로 처리)
      await updateReview(reviewId, {
        rating: editReviewForm.rating,
        content: editReviewForm.content || null,
        imageFilePaths: imageFilePaths ?? [],
      });
      setEditingReviewId(null);
      setEditReviewImagesById((prev) => {
        const next = { ...prev };
        delete next[reviewId];
        return next;
      });
      loadReviews();
      // 리뷰 수정 후에도 상품의 review_status를 한 번 동기화 (상태는 ALREADY_REVIEWED 유지)
      refreshProductReviewStatus();
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
      // 리뷰 삭제 후 상품의 review_status를 최신 상태로 갱신 (예: CAN_REVIEW 등)
      refreshProductReviewStatus();
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
        if (err.name === 'AbortError') return;
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
    return () => {
      abortController.abort();
    };
  }, [id]);

  if (loading) {
    return <LoadingModal isOpen={true} message="로딩 중..." />;
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
              <div className="w-full aspect-square bg-bg-surface rounded-token flex items-center justify-center">
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
            <h1 className="text-3xl font-bold text-text-main font-emphasis mb-4">{product.name}</h1>

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
              <div className="text-4xl font-bold text-primary-500 font-emphasis mb-2">
                {displayPrice != null ? displayPrice.toLocaleString() : '-'}원
              </div>
              {hasVariants && selectedVariant && (
                <p className="text-sm text-text-sub">선택된 옵션: {selectedVariant.optionText}</p>
              )}
            </div>

            <div className="mb-6">
              <h2 className="text-xl font-semibold text-text-main font-emphasis mb-2">상품 설명</h2>
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
                          className={`px-4 py-2 rounded-token font-medium transition border-2 ${
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
              <h2 className="text-xl font-semibold text-text-main font-emphasis mb-3">수량 선택</h2>
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
            <h2 className="text-xl font-semibold text-text-main font-emphasis">상품 리뷰</h2>
            {product.reviewSummary != null && product.reviewSummary.count > 0 && (
              <span className="text-text-sub text-sm">
                평점 {Number(product.reviewSummary.average_rating ?? product.reviewSummary.averageRating).toFixed(1)} (리뷰 {product.reviewSummary.count}개)
              </span>
            )}
          </div>
          {reviewsData.total !== undefined && product.reviewSummary == null && (
            <span className="text-text-muted text-sm">총 {reviewsData.total}개</span>
          )}
          {isLoggedIn ? (
            (() => {
              const reviewStatus = product?.review_status ?? product?.reviewStatus ?? null;

              if (reviewStatus === 'CAN_REVIEW') {
                return (
                  <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={() => setReviewFormOpen((prev) => !prev)}
                  >
                    {reviewFormOpen ? '취소' : '리뷰 작성'}
                  </Button>
                );
              }

              if (reviewStatus === 'ALREADY_REVIEWED') {
                return (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled
                    className="opacity-50 cursor-not-allowed"
                  >
                    리뷰가 존재합니다
                  </Button>
                );
              }

              if (reviewStatus === 'NOT_PURCHASED') {
                return (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled
                    className="opacity-50 cursor-not-allowed"
                  >
                    구매 후 작성 가능
                  </Button>
                );
              }

              return null;
            })()
          ) : null}
        </div>

        {reviewFormOpen && (
          <form
            onSubmit={handleSubmitReview}
            className="mb-6 p-4 bg-bg-surface rounded-lg"
            onDragOver={(e) => {
              e.preventDefault();
              e.stopPropagation();
            }}
            onDrop={(e) => {
              e.preventDefault();
              e.stopPropagation();
              const files = Array.from(e.dataTransfer.files || []).filter((file) =>
                file.type.startsWith('image/'),
              );
              if (files.length === 0) return;
              setReviewImages((prev) => {
                const next = [...prev, ...files];
                if (next.length > 10) {
                  alert('리뷰 이미지는 최대 10장까지 선택할 수 있습니다.');
                  return next.slice(0, 10);
                }
                return next;
              });
            }}
          >
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
            <div className="mb-3">
              <label className="block text-sm font-medium text-text-main mb-1">
                리뷰 이미지 (선택, 최대 10장)
              </label>
              <p className="text-xs text-text-muted mb-1">
                아래 영역에 이미지를 드래그 앤 드랍해도 첨부됩니다.
              </p>
              <input
                type="file"
                accept="image/*"
                multiple
                onChange={(e) => {
                  const files = Array.from(e.target.files || []);
                  setReviewImages((prev) => {
                    const next = [...prev, ...files];
                    if (next.length > 10) {
                      alert('리뷰 이미지는 최대 10장까지 선택할 수 있습니다.');
                      return next.slice(0, 10);
                    }
                    return next;
                  });
                }}
                className="block w-full text-sm text-text-sub file:mr-4 file:py-2 file:px-4 file:rounded-token file:border-0 file:text-sm file:font-semibold file:bg-primary-500/10 file:text-primary-500 hover:file:bg-primary-500/20"
              />
              {reviewImages.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-2">
                  {reviewImages.map((file, index) => (
                    <div
                      key={`${file.name}-${index}`}
                      className="relative w-16 h-16 rounded-md overflow-hidden border border-border-default bg-bg-card"
                    >
                      <img src={URL.createObjectURL(file)} alt={file.name} className="w-full h-full object-cover" />
                      <button
                        type="button"
                        onClick={() =>
                          setReviewImages((prev) => prev.filter((_, i) => i !== index))
                        }
                        className="absolute -top-1 -right-1 bg-bg-root/80 text-xs text-primary-500 rounded-full px-1"
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
              )}
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
                        <form
                          onSubmit={(e) => handleUpdateReview(e, review.id)}
                          className="mt-2"
                          onDragOver={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                          }}
                          onDrop={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            const files = Array.from(e.dataTransfer.files || []).filter(
                              (file) => file.type.startsWith('image/'),
                            );
                            if (files.length === 0) return;
                            setEditReviewImagesById((prev) => {
                              const current = prev[review.id] || {
                                existing: review.images || [],
                                files: [],
                              };
                              const nextFiles = [...current.files, ...files];
                              const totalCount =
                                (current.existing?.length || 0) + nextFiles.length;
                              if (totalCount > 10) {
                                alert('리뷰 이미지는 최대 10장까지 선택할 수 있습니다.');
                              }
                              return {
                                ...prev,
                                [review.id]: {
                                  ...current,
                                  files: totalCount > 10 ? nextFiles.slice(0, 10 - (current.existing?.length || 0)) : nextFiles,
                                },
                              };
                            });
                          }}
                        >
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
                            onChange={(e) =>
                              setEditReviewForm((prev) => ({ ...prev, content: e.target.value }))
                            }
                            rows={2}
                            className="border border-border-default rounded px-2 py-1 w-full text-sm block mb-2 bg-bg-card text-text-main"
                          />
                          <div className="mb-2">
                            <label className="block text-xs font-medium text-text-main mb-1">
                              리뷰 이미지 수정 (최대 10장)
                            </label>
                            <p className="text-[11px] text-text-muted mb-1">
                              이미지를 드래그 앤 드랍하거나 파일 선택으로 추가하세요. 삭제한 기존 이미지는
                              저장 시 제거됩니다.
                            </p>
                            <input
                              type="file"
                              accept="image/*"
                              multiple
                              onChange={(e) => {
                                const files = Array.from(e.target.files || []);
                                setEditReviewImagesById((prev) => {
                                  const current = prev[review.id] || {
                                    existing: review.images || [],
                                    files: [],
                                  };
                                  const nextFiles = [...current.files, ...files];
                                  const totalCount =
                                    (current.existing?.length || 0) + nextFiles.length;
                                  if (totalCount > 10) {
                                    alert('리뷰 이미지는 최대 10장까지 선택할 수 있습니다.');
                                  }
                                  return {
                                    ...prev,
                                    [review.id]: {
                                      ...current,
                                      files:
                                        totalCount > 10
                                          ? nextFiles.slice(
                                              0,
                                              10 - (current.existing?.length || 0),
                                            )
                                          : nextFiles,
                                    },
                                  };
                                });
                              }}
                              className="block w-full text-xs text-text-sub file:mr-3 file:py-1.5 file:px-3 file:rounded-token file:border-0 file:text-xs file:font-semibold file:bg-primary-500/10 file:text-primary-500 hover:file:bg-primary-500/20"
                            />
                            {(() => {
                              const state = editReviewImagesById[review.id] || {
                                existing: review.images || [],
                                files: [],
                              };
                              const existing = state.existing || [];
                              const files = state.files || [];
                              return (
                                <>
                                  {(existing.length > 0 || files.length > 0) && (
                                    <div className="mt-2 flex flex-wrap gap-2">
                                      {existing.map((image, idx) => (
                                        <div
                                          key={`${image.uuid || image.filePath || image.url || idx}`}
                                          className="relative flex-shrink-0 w-16 h-16 rounded-md overflow-visible border border-border-default bg-bg-card"
                                        >
                                          <div className="w-full h-full overflow-hidden rounded-md">
                                            {image.url ? (
                                              <img
                                                src={image.url}
                                                alt={`기존 이미지 ${idx + 1}`}
                                                className="w-full h-full object-cover"
                                                onError={(e) => {
                                                  e.target.src =
                                                    'https://via.placeholder.com/64x64?text=No+Image';
                                                }}
                                              />
                                            ) : (
                                              <span className="text-[10px] text-text-muted px-1">
                                                이미지 없음
                                              </span>
                                            )}
                                          </div>
                                          <button
                                            type="button"
                                            onClick={() =>
                                              setEditReviewImagesById((prev) => {
                                                const current = prev[review.id] || {
                                                  existing: review.images || [],
                                                  files: [],
                                                };
                                                const nextExisting = current.existing.filter(
                                                  (_img, i) => i !== idx,
                                                );
                                                return {
                                                  ...prev,
                                                  [review.id]: {
                                                    ...current,
                                                    existing: nextExisting,
                                                  },
                                                };
                                              })
                                            }
                                            className="absolute top-0 right-0 bg-bg-root/90 text-xs text-primary-500 rounded-full w-5 h-5 flex items-center justify-center hover:bg-primary-500 hover:text-bg-root transition"
                                            title="삭제"
                                          >
                                            ×
                                          </button>
                                        </div>
                                      ))}
                                      {files.map((file, idx) => (
                                        <div
                                          key={`${file.name}-${idx}`}
                                          className="relative flex-shrink-0 w-16 h-16 rounded-md overflow-visible border border-border-default bg-bg-card"
                                        >
                                          <div className="w-full h-full overflow-hidden rounded-md">
                                            <img
                                              src={URL.createObjectURL(file)}
                                              alt={file.name}
                                              className="w-full h-full object-cover"
                                            />
                                          </div>
                                          <button
                                            type="button"
                                            onClick={() =>
                                              setEditReviewImagesById((prev) => {
                                                const current = prev[review.id] || {
                                                  existing: review.images || [],
                                                  files: [],
                                                };
                                                const nextFiles = current.files.filter(
                                                  (_f, i) => i !== idx,
                                                );
                                                return {
                                                  ...prev,
                                                  [review.id]: {
                                                    ...current,
                                                    files: nextFiles,
                                                  },
                                                };
                                              })
                                            }
                                            className="absolute top-0 right-0 bg-bg-root/90 text-xs text-primary-500 rounded-full w-5 h-5 flex items-center justify-center hover:bg-primary-500 hover:text-bg-root transition"
                                            title="삭제"
                                          >
                                            ×
                                          </button>
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </>
                              );
                            })()}
                          </div>
                          <div className="flex gap-2 mt-2">
                            <button
                              type="submit"
                              disabled={submitting}
                              className="text-sm text-primary-500 hover:underline"
                            >
                              저장
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setEditingReviewId(null);
                                setEditReviewImagesById((prev) => {
                                  const next = { ...prev };
                                  delete next[review.id];
                                  return next;
                                });
                              }}
                              className="text-sm text-text-muted hover:underline"
                            >
                              취소
                            </button>
                          </div>
                        </form>
                      ) : (
                        <p className="text-text-sub text-sm whitespace-pre-wrap">{review.content || '(내용 없음)'}</p>
                      )}
                    </div>
                    {!editingReviewId &&
                      currentMemberId != null &&
                      (review.member_id === Number(currentMemberId) || review.memberId === Number(currentMemberId)) && (
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
                          <button
                            type="button"
                            onClick={() => handleDeleteReview(review.id)}
                            className="text-xs text-primary-400 hover:underline"
                          >
                            삭제
                          </button>
                        </div>
                      )}
                  </div>
                  {!editingReviewId && review.images && review.images.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-2">
                      {review.images.map((image, idx) => (
                        <button
                          key={`${image.uuid || image.filePath || image.url || idx}`}
                          type="button"
                          className="flex-shrink-0 w-16 h-16 rounded-md overflow-hidden border border-border-default bg-bg-card"
                          onClick={() => {
                            if (image.url) {
                              window.open(image.url, '_blank', 'noopener,noreferrer');
                            }
                          }}
                        >
                          {image.url ? (
                            <img
                              src={image.url}
                              alt={`리뷰 이미지 ${idx + 1}`}
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                e.target.src =
                                  'https://via.placeholder.com/64x64?text=No+Image';
                              }}
                            />
                          ) : (
                            <span className="text-[10px] text-text-muted px-1">
                              이미지 없음
                            </span>
                          )}
                        </button>
                      ))}
                    </div>
                  )}
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

