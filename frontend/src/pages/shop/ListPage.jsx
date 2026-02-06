import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Search, RotateCcw, LayoutGrid, UtensilsCrossed, Pill, Dumbbell, Shirt, Package } from 'lucide-react';
import { getProductList } from '../../services/productApi';
import ProductCard from '../../components/shop/ProductCard';
import Button from '../../components/common/Button';
import LoadingModal from '../../components/common/LoadingModal';

const SEGMENT_CARDS = [
  { id: null, label: '전체', Icon: LayoutGrid },
  { id: 1, label: '음식', Icon: UtensilsCrossed },
  { id: 2, label: '보충제', Icon: Pill },
  { id: 3, label: '헬스용품', Icon: Dumbbell },
  { id: 4, label: '의류', Icon: Shirt },
  { id: 5, label: '기타', Icon: Package },
];

const ProductList = () => {
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);
  const isAdmin = !!loginState?.roleNames?.includes('ADMIN');
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchType, setSearchType] = useState('all');
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const productsRef = useRef(null);

  useEffect(() => {
    const abortController = new AbortController();

    const loadProducts = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await getProductList({
          page,
          page_size: pageSize,
          keyword,
          searchType,
          categoryId: selectedCategoryId,
          signal: abortController.signal,
        });

        if (abortController.signal.aborted) return;

        setProducts(response.items || []);
        setTotal(response.total || 0);
        setHasNext(response.has_next || false);
        setHasPrevious(response.has_previous || false);
      } catch (err) {
        if (err.name === 'AbortError') return;
        if (abortController.signal.aborted) return;
        setError(err.message || '상품 목록을 불러오는데 실패했습니다.');
        console.error('Failed to load products:', err);
      } finally {
        if (!abortController.signal.aborted) {
          setLoading(false);
        }
      }
    };

    loadProducts();
    return () => {
      abortController.abort();
    };
  }, [page, keyword, searchType, selectedCategoryId]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [page]);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(searchInput);
    setPage(1);
  };

  const handleResetSearch = () => {
    setSearchInput('');
    setKeyword('');
    setSearchType('all');
    setPage(1);
  };

  const hasActiveSearch = keyword || searchType !== 'all';

  const handleCategoryFilter = (categoryId) => {
    setSelectedCategoryId(categoryId === selectedCategoryId ? null : categoryId);
    setPage(1);
  };

  const handleScrollToProducts = () => {
    productsRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
  };

  const getPrimaryImageUrl = (product) => {
    if (!product.images || product.images.length === 0) {
      return 'https://via.placeholder.com/300x300?text=No+Image';
    }
    const primaryImage = product.images.find((img) => img.primaryImage);
    return primaryImage ? primaryImage.url : product.images[0].url;
  };

  if (loading && products.length === 0) {
    return <LoadingModal isOpen={true} message="로딩 중..." />;
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[40vh] gap-token-4">
        <p className="text-accent-secondary font-medium">{error}</p>
        <Button variant="ghost" onClick={() => window.location.reload()}>
          다시 시도
        </Button>
      </div>
    );
  }

  return (
    <div className="w-full bg-bg-root">
      <section className="relative min-h-0 flex flex-col justify-center py-8 sm:py-10 lg:py-12 overflow-hidden">
        <div
          className="absolute inset-0 bg-gradient-to-br from-bg-root via-gray-100/30 to-bg-root"
          aria-hidden
        />
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage: `linear-gradient(var(--primary-500) 1px, transparent 1px),
              linear-gradient(90deg, var(--primary-500) 1px, transparent 1px)`,
            backgroundSize: '48px 48px',
          }}
          aria-hidden
        />
        <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-bg-root to-transparent" aria-hidden />

        <div className="relative z-10 container-token">
          <div className="flex flex-wrap items-start justify-between gap-token-6">
            <div>
              <h1 className="section-title" style={{ fontSize: 'clamp(2.25rem, 6vw, 3.75rem)' }}>
                <span className="text-text-main">TRAIN.</span><br />
                <span className="text-primary-500">FUEL.</span><br />
                <span className="text-text-main">PERFORM.</span>
              </h1>
              <p className="section-desc mt-2 max-w-md">
                지금 필요한 보충제로 퍼포먼스를 올려보세요.
              </p>
              <Button
                type="button"
                variant="primary"
                size="md"
                onClick={handleScrollToProducts}
                className="mt-4 bg-transparent hover:scale-[1.02] border-primary-500"
                style={{
                  backgroundImage: 'var(--gradient-cta)',
                  backgroundSize: '200% 100%',
                  backgroundPosition: '0% 50%',
                }}
              >
                지금 필요한 보충제 찾기
              </Button>
            </div>
            {isAdmin && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => navigate('/shop/admin/create')}
              >
                상품 등록
              </Button>
            )}
          </div>
        </div>
      </section>

      <section className="relative z-10 container-token -mt-2 mb-token-4">
        <div className="flex flex-wrap items-center gap-2">
          {SEGMENT_CARDS.map(({ id, label, Icon }) => {
            const isSelected = selectedCategoryId === id;
            return (
              <button
                key={id ?? 'all'}
                type="button"
                onClick={() => handleCategoryFilter(id)}
                className={`segment-btn ${isSelected ? 'segment-btn-active' : ''}`}
              >
                <Icon className="w-5 h-5 shrink-0" strokeWidth={2} />
                <span>{label}</span>
              </button>
            );
          })}
        </div>
      </section>

      <section className="container-token mb-token-8">
        <form onSubmit={handleSearch} className="max-w-2xl flex flex-wrap items-stretch gap-2">
          <select
            value={searchType}
            onChange={(e) => setSearchType(e.target.value)}
            className="select-token w-auto min-w-[140px] h-11"
            aria-label="검색 대상"
          >
            <option value="all">전체</option>
            <option value="name">상품명</option>
            <option value="description">상품내용</option>
          </select>
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-text-muted pointer-events-none" aria-hidden />
            <input
              id="product-search"
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="상품 검색..."
              className="input-token input-token-with-icon pr-24 w-full h-11"
              aria-label="상품 검색"
            />
            <Button
              type="submit"
              variant="primary"
              size="md"
              className="absolute right-1.5 top-1/2 -translate-y-1/2 h-9 bg-gradient-to-r from-primary-600 to-primary-500 hover:shadow-glow transition-all duration-200 ease-out-quart"
            >
              검색
            </Button>
          </div>
          {hasActiveSearch && (
            <Button
              type="button"
              variant="ghost"
              size="md"
              onClick={handleResetSearch}
              className="shrink-0 h-11 gap-1.5 text-text-sub hover:text-text-main"
              aria-label="검색 초기화"
            >
              <RotateCcw className="w-4 h-4" strokeWidth={2} />
              검색 초기화
            </Button>
          )}
        </form>
      </section>

      <section ref={productsRef} className="container-token pb-14">
        {products.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 gap-token-4 text-center">
            <p className="text-text-muted text-lg">등록된 상품이 없습니다.</p>
          </div>
        ) : (
          <>
            <header className="section-header-token">
              <h2 className="section-title text-text-main">
                상품 목록
              </h2>
              <p className="section-desc">{total}개의 상품</p>
            </header>

            <div
              className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5"
              role="list"
              aria-label="상품 목록"
            >
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  displayPrice={product.basePrice ?? 0}
                  getPrimaryImageUrl={getPrimaryImageUrl}
                />
              ))}
            </div>

            {total > 0 && (() => {
              const totalPages = Math.ceil(total / pageSize);
              const getDisplayPages = () => {
                if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
                const pages = [1];
                if (page > 3) pages.push('ellipsis-start');
                const midStart = Math.max(2, page - 1);
                const midEnd = Math.min(totalPages - 1, page + 1);
                for (let p = midStart; p <= midEnd; p++) pages.push(p);
                if (page < totalPages - 2) pages.push('ellipsis-end');
                if (totalPages > 1) pages.push(totalPages);
                return pages;
              };
              return (
                <nav
                  className="flex flex-wrap justify-center items-center gap-1 sm:gap-2 pt-token-8 border-t border-border-default"
                  aria-label="페이지 네비게이션"
                >
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => handlePageChange(1)}
                    disabled={page <= 1}
                    aria-label="맨 앞"
                  >
                    맨 앞
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => handlePageChange(page - 1)}
                    disabled={!hasPrevious}
                    aria-label="이전 페이지"
                  >
                    이전
                  </Button>
                  <div className="flex items-center gap-1 mx-1">
                    {getDisplayPages().map((p, i) =>
                      p === 'ellipsis-start' || p === 'ellipsis-end' ? (
                        <span key={`ellipsis-${i}`} className="px-1 text-text-muted">
                          …
                        </span>
                      ) : (
                        <button
                          key={p}
                          type="button"
                          onClick={() => handlePageChange(p)}
                          className={`page-btn ${page === p ? 'page-btn-active' : ''}`}
                          aria-label={`${p}페이지`}
                          aria-current={page === p ? 'page' : undefined}
                        >
                          {p}
                        </button>
                      )
                    )}
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => handlePageChange(page + 1)}
                    disabled={!hasNext}
                    aria-label="다음 페이지"
                  >
                    다음
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => handlePageChange(totalPages)}
                    disabled={page >= totalPages}
                    aria-label="맨 뒤"
                  >
                    맨 뒤
                  </Button>
                </nav>
              );
            })()}
          </>
        )}
      </section>
    </div>
  );
};

export default ProductList;
