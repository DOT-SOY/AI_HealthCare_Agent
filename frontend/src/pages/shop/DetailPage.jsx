import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { getProduct } from '../../services/productApi';
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
    </div>
  );
};

export default ProductDetail;

