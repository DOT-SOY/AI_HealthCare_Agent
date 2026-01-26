import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getProductList } from '../../services/productApi';

const ProductList = () => {
  const navigate = useNavigate();
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
  
  // 카테고리 타입 정의 (CategoryType enum과 일치)
  // TODO: 실제 카테고리 API가 추가되면 동적으로 가져오도록 수정
  const categoryTypes = [
    { type: 'FOOD', name: '음식' },
    { type: 'SUPPLEMENT', name: '보충제' },
    { type: 'HEALTH_GOODS', name: '헬스용품' },
    { type: 'CLOTHING', name: '의류' },
    { type: 'ETC', name: '기타' },
  ];
  
  // 카테고리 타입을 카테고리 ID로 매핑 (임시)
  // 실제로는 백엔드에서 카테고리 API를 통해 동적으로 가져와야 합니다
  const categoryTypeToIdMap = {
    'FOOD': 1,
    'SUPPLEMENT': 2,
    'HEALTH_GOODS': 3,
    'CLOTHING': 4,
    'ETC': 5,
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
          signal: abortController.signal, // AbortController signal 전달
        });
        
        // 요청이 취소되었는지 확인
        if (abortController.signal.aborted) return;
        
        setProducts(response.items || []);
        setTotal(response.total || 0);
        setHasNext(response.has_next || false);
        setHasPrevious(response.has_previous || false);
      } catch (err) {
        // AbortError는 무시 (요청 취소)
        if (err.name === 'AbortError') return;
        
        // 요청이 취소되었는지 확인
        if (abortController.signal.aborted) return;
        
        setError(err.message || '상품 목록을 불러오는데 실패했습니다.');
        console.error('Failed to load products:', err);
      } finally {
        // 요청이 취소되었는지 확인
        if (!abortController.signal.aborted) {
          setLoading(false);
        }
      }
    };
    
    loadProducts();
    
    // cleanup: 컴포넌트 언마운트 또는 의존성 변경 시 이전 요청 취소
    return () => {
      abortController.abort();
    };
  }, [page, keyword, selectedCategoryId]);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(searchInput);
    setPage(1); // 검색 시 첫 페이지로
  };

  const handleCategoryFilter = (categoryId) => {
    setSelectedCategoryId(categoryId === selectedCategoryId ? null : categoryId);
    setPage(1); // 카테고리 필터 변경 시 첫 페이지로
  };

  const handlePageChange = (newPage) => {
    setPage(newPage);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // 대표 이미지 URL 가져오기
  const getPrimaryImageUrl = (product) => {
    if (!product.images || product.images.length === 0) {
      return 'https://via.placeholder.com/300x300?text=No+Image';
    }
    const primaryImage = product.images.find(img => img.primaryImage);
    return primaryImage ? primaryImage.url : product.images[0].url;
  };

  if (loading && products.length === 0) {
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
      </div>
    );
  }

  return (
    <div className="w-full">
      <div className="mb-8">
        <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
          <h1 className="text-3xl font-bold">상품 목록</h1>
          <button
            type="button"
            onClick={() => navigate('/shop/admin/create')}
            className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition font-medium"
          >
            상품 등록
          </button>
        </div>
        
        {/* 검색 폼 */}
        <form onSubmit={handleSearch} className="mb-6">
          <div className="flex gap-2">
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="상품명으로 검색..."
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="submit"
              className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition"
            >
              검색
            </button>
          </div>
        </form>

        {/* 카테고리 필터 */}
        <div className="mb-6">
          <h2 className="text-lg font-semibold mb-3">카테고리</h2>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => handleCategoryFilter(null)}
              className={`px-4 py-2 rounded-lg font-medium transition ${
                selectedCategoryId === null
                  ? 'bg-blue-500 text-white hover:bg-blue-600 shadow-md'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
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
                  onClick={() => {
                    handleCategoryFilter(categoryId);
                  }}
                  className={`px-4 py-2 rounded-lg font-medium transition ${
                    selectedCategoryId === categoryId
                      ? 'bg-blue-500 text-white hover:bg-blue-600 shadow-md'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {category.name}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* 상품 그리드 */}
      {products.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          상품이 없습니다.
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 mb-8">
            {products.map((product) => (
              <Link
                key={product.id}
                to={`/shop/detail/${product.id}`}
                className="bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow"
              >
                <div className="aspect-square bg-gray-100 overflow-hidden">
                  <img
                    src={getPrimaryImageUrl(product)}
                    alt={product.name}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      e.target.src = 'https://via.placeholder.com/300x300?text=No+Image';
                    }}
                  />
                </div>
                <div className="p-4">
                  <h3 className="font-semibold text-lg mb-2 line-clamp-2">{product.name}</h3>
                  <p className="text-gray-600 text-sm mb-3 line-clamp-2">{product.description}</p>
                  <div className="flex justify-between items-center">
                    <span className="text-2xl font-bold text-blue-600">
                      {product.basePrice?.toLocaleString()}원
                    </span>
                    <span className={`px-2 py-1 rounded text-xs ${
                      product.status === 'ACTIVE' 
                        ? 'bg-green-100 text-green-800' 
                        : 'bg-gray-100 text-gray-800'
                    }`}>
                      {product.status === 'ACTIVE' ? '판매중' : '품절'}
                    </span>
                  </div>
                </div>
              </Link>
            ))}
          </div>

          {/* 페이지네이션 */}
          {total > 0 && (
            <div className="flex justify-center items-center gap-2">
              <button
                onClick={() => handlePageChange(page - 1)}
                disabled={!hasPrevious}
                className="px-4 py-2 border rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100"
              >
                이전
              </button>
              <span className="px-4 py-2">
                {page} / {Math.ceil(total / pageSize)}
              </span>
              <button
                onClick={() => handlePageChange(page + 1)}
                disabled={!hasNext}
                className="px-4 py-2 border rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default ProductList;
