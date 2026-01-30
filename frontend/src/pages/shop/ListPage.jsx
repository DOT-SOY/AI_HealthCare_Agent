import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { getProductList } from '../../services/productApi';
import { useCart } from '../../components/layout/ShopLayout';
import ProductCard from '../../components/shop/ProductCard';
import Button from '../../components/common/Button';

const ProductList = () => {
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);
  const isAdmin = !!loginState?.roleNames?.includes('ADMIN');
  const { addToCart } = useCart();
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
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const [productQtys, setProductQtys] = useState({});
  const [selectedVariants, setSelectedVariants] = useState({});
  const isAddingToCartRef = useRef(false);

  const categoryTypes = [
    { type: 'FOOD', name: '음식' },
    { type: 'SUPPLEMENT', name: '보충제' },
    { type: 'HEALTH_GOODS', name: '헬스용품' },
    { type: 'CLOTHING', name: '의류' },
    { type: 'ETC', name: '기타' },
  ];

  const categoryTypeToIdMap = {
    FOOD: 1,
    SUPPLEMENT: 2,
    HEALTH_GOODS: 3,
    CLOTHING: 4,
    ETC: 5,
  };

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
  }, [page, keyword, selectedCategoryId]);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(searchInput);
    setPage(1);
  };

  const handleCategoryFilter = (categoryId) => {
    setSelectedCategoryId(categoryId === selectedCategoryId ? null : categoryId);
    setPage(1);
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleAddToCart = (e, product) => {
    if (isAddingToCartRef.current) return;
    e.preventDefault();
    e.stopPropagation();
    isAddingToCartRef.current = true;

    const hasVariants = product.variants && product.variants.length > 0;
    const selectedVariant = hasVariants ? selectedVariants[product.id] : null;

    if (hasVariants && !selectedVariant) {
      isAddingToCartRef.current = false;
      return;
    }

    const currentQty = productQtys[product.id] ?? 1;
    addToCart(product, selectedVariant, currentQty);

    setTimeout(() => {
      isAddingToCartRef.current = false;
    }, 0);
  };

  const handleQtyChange = (productId, newQty) => {
    setProductQtys((prev) => ({
      ...prev,
      [productId]: newQty,
    }));
  };

  const handleVariantChange = (productId, variant) => {
    setSelectedVariants((prev) => ({
      ...prev,
      [productId]: variant,
    }));
  };

  const getPrimaryImageUrl = (product) => {
    if (!product.images || product.images.length === 0) {
      return 'https://via.placeholder.com/300x300?text=No+Image';
    }
    const primaryImage = product.images.find((img) => img.primaryImage);
    return primaryImage ? primaryImage.url : product.images[0].url;
  };

  const getDisplayPrice = (product) => {
    const selectedVariant = selectedVariants[product.id];
    if (selectedVariant && selectedVariant.price != null) {
      return Number(selectedVariant.price);
    }
    return product.basePrice;
  };

  if (loading && products.length === 0) {
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
      </div>
    );
  }

  return (
    <div className="w-full">
      <div className="mb-8">
        <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
          <h1 className="text-3xl font-bold text-text-main">상품 목록</h1>
          {isAdmin && (
            <Button
              type="button"
              variant="primary"
              onClick={() => navigate('/shop/admin/create')}
            >
              상품 등록
            </Button>
          )}
        </div>

        <form onSubmit={handleSearch} className="mb-6">
          <div className="flex gap-2">
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="상품명으로 검색..."
              className="flex-1 px-4 py-2 border border-border-default rounded-lg bg-bg-card text-text-main placeholder-text-muted focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <Button type="submit" variant="primary">
              검색
            </Button>
          </div>
        </form>

        <div className="mb-6">
          <h2 className="text-lg font-semibold text-text-main mb-3">카테고리</h2>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => handleCategoryFilter(null)}
              className={`px-4 py-2 rounded-lg font-medium transition ${
                selectedCategoryId === null
                  ? 'bg-primary-500 text-bg-root hover:shadow-glow'
                  : 'bg-bg-card text-text-main border border-border-default hover:border-primary-500'
              }`}
            >
              전체
            </button>
            {categoryTypes.map((category) => {
              const categoryId = categoryTypeToIdMap[category.type];
              return (
                <button
                  key={category.type}
                  type="button"
                  onClick={() => handleCategoryFilter(categoryId)}
                  className={`px-4 py-2 rounded-lg font-medium transition ${
                    selectedCategoryId === categoryId
                      ? 'bg-primary-500 text-bg-root hover:shadow-glow'
                      : 'bg-bg-card text-text-main border border-border-default hover:border-primary-500'
                  }`}
                >
                  {category.name}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {products.length === 0 ? (
        <div className="text-center py-12 text-text-muted">상품이 없습니다.</div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 mb-8">
            {products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                displayPrice={getDisplayPrice(product)}
                selectedVariant={selectedVariants[product.id]}
                onVariantChange={handleVariantChange}
                qty={productQtys[product.id] ?? 1}
                onQtyChange={handleQtyChange}
                onAddToCart={handleAddToCart}
                isAddingDisabled={
                  product.status !== 'ACTIVE' ||
                  (product.variants &&
                    product.variants.filter((v) => v.active).length > 0 &&
                    !selectedVariants[product.id])
                }
                getPrimaryImageUrl={getPrimaryImageUrl}
              />
            ))}
          </div>

          {total > 0 && (
            <div className="flex justify-center items-center gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => handlePageChange(page - 1)}
                disabled={!hasPrevious}
              >
                이전
              </Button>
              <span className="px-4 py-2 text-text-main">
                {page} / {Math.ceil(total / pageSize)}
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => handlePageChange(page + 1)}
                disabled={!hasNext}
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default ProductList;
