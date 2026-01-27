import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { getProduct, updateProduct } from '../../../services/productApi';
import { uploadFiles } from '../../../services/fileApi';
import { CATEGORY_TYPES } from '../../../constants/categoryTypes';

const ProductEditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    basePrice: '',
    status: 'DRAFT',
  });
  const [existingImages, setExistingImages] = useState([]);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [isUploading, setIsUploading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [selectedCategoryTypes, setSelectedCategoryTypes] = useState([]);
  const [variants, setVariants] = useState([]);

  useEffect(() => {
    if (!loginState?.roleNames?.includes('ADMIN')) {
      alert('접근 권한이 없습니다 (관리자 전용)');
      navigate('/shop/list', { replace: true });
    }
  }, [loginState, navigate]);

  useEffect(() => {
    if (loginState?.roleNames?.includes('ADMIN') && id) {
      loadProduct();
    }
  // loadProduct는 id 기반으로 내부에서 사용하므로 id 변경 시만 재실행
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, loginState?.roleNames]);

  const toggleCategory = (value) => {
    setSelectedCategoryTypes((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  };

  const loadProduct = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getProduct(id);
      setProduct(data);
      setFormData({
        name: data.name || '',
        description: data.description || '',
        basePrice: data.basePrice?.toString() || '',
        status: data.status || 'DRAFT',
      });
      setExistingImages(data.images || []);
      
      // Categories 설정
      if (data.categories && data.categories.length > 0) {
        setSelectedCategoryTypes(data.categories.map((c) => c.categoryType).filter(Boolean));
      }
      
      // Variants 설정
      if (data.variants && data.variants.length > 0) {
        setVariants(data.variants.map(v => ({
          id: v.id,
          sku: v.sku || '',
          optionDisplay: v.optionText ?? '',
          price: v.price ? v.price.toString() : '',
          stockQty: v.stockQty || 0,
          active: v.active !== undefined ? v.active : true,
        })));
      }
    } catch (err) {
      setError(err.message || '상품 정보를 불러오는데 실패했습니다.');
      console.error('Failed to load product:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleFileSelect = (e) => {
    const files = Array.from(e.target.files);
    setSelectedFiles(prev => [...prev, ...files]);
  };

  const handleRemoveExistingImage = (uuid) => {
    setExistingImages(prev => prev.filter(img => img.uuid !== uuid));
  };

  const handleRemoveNewFile = (index) => {
    setSelectedFiles(prev => prev.filter((_, i) => i !== index));
    setUploadedFiles(prev => prev.filter((_, i) => i !== index));
    setUploadProgress(prev => {
      const newProgress = { ...prev };
      delete newProgress[index];
      return newProgress;
    });
  };

  const handleUploadFiles = async () => {
    if (selectedFiles.length === 0) {
      setError('업로드할 파일을 선택해주세요.');
      return;
    }

    try {
      setIsUploading(true);
      setError(null);

      // 업로드 진행률 초기화
      const progress = {};
      selectedFiles.forEach((_, index) => {
        progress[index] = 0;
      });
      setUploadProgress(progress);

      // 파일 업로드
      const results = await uploadFiles(
        selectedFiles,
        'products',
        (fileIndex, percent) => {
          setUploadProgress(prev => ({
            ...prev,
            [fileIndex]: percent,
          }));
        }
      );

      setUploadedFiles(prev => [...prev, ...results]);
      setSelectedFiles([]); // 업로드 완료 후 선택 파일 초기화
    } catch (err) {
      setError(err.message || '파일 업로드에 실패했습니다.');
      console.error('Upload failed:', err);
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!formData.name || !formData.description || !formData.basePrice) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);

      // 최종 이미지 filePath 수집 (유지할 기존 이미지 + 새로 업로드한 파일)
      const existingFilePaths = existingImages
        .filter(img => img.filePath) // filePath가 있는 이미지만
        .map(img => img.filePath);
      
      const allImageFilePaths = [
        ...existingFilePaths,
        ...uploadedFiles.map(file => file.filePath),
      ];

      // 덮어쓰기 방식: 최종 이미지 목록만 전송
      // 빈 배열이면 모든 이미지 제거, null이면 기존 이미지 유지
      const productData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        basePrice: parseFloat(formData.basePrice),
        status: formData.status,
        imageFilePaths: allImageFilePaths.length > 0 ? allImageFilePaths : [],
        variants: variants.length > 0 ? variants.map(v => ({
          sku: v.sku,
          optionText: (v.optionDisplay ?? '').trim(),
          price: v.price ? parseFloat(v.price) : null,
          stockQty: parseInt(v.stockQty) || 0,
          active: v.active !== undefined ? v.active : true,
        })) : [],
        categoryTypes: selectedCategoryTypes.length > 0 ? selectedCategoryTypes : [],
      };

      await updateProduct(id, productData);
      navigate(`/shop/detail/${id}`);
    } catch (err) {
      setError(err.message || '상품 수정에 실패했습니다.');
      console.error('Update failed:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-lg">로딩 중...</div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div>상품을 찾을 수 없습니다.</div>
        <button
          onClick={() => navigate('/shop/list')}
          className="ml-4 text-blue-500 hover:underline"
        >
          목록으로 돌아가기
        </button>
      </div>
    );
  }

  // 최종 이미지 목록 (기존 + 신규)
  const finalImages = [
    ...existingImages.map(img => ({ ...img, isExisting: true })),
    ...uploadedFiles.map((file, index) => ({
      uuid: `new-${index}`,
      url: file.url,
      filePath: file.filePath,
      primaryImage: false,
      isExisting: false,
    })),
  ];

  return (
    <div className="w-full max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold mb-6">상품 수정</h1>

      {error && (
        <div className="mb-4 p-4 bg-red-100 border border-red-400 text-red-700 rounded">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* 기본 정보 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-xl font-semibold mb-4">기본 정보</h2>
          
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-2">
                상품명 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleInputChange}
                required
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-2">
                상품 설명 <span className="text-red-500">*</span>
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                required
                rows={5}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-2">
                가격 <span className="text-red-500">*</span>
              </label>
              <input
                type="number"
                name="basePrice"
                value={formData.basePrice}
                onChange={handleInputChange}
                required
                min="0"
                step="0.01"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-2">
                판매 상태
              </label>
              <select
                name="status"
                value={formData.status}
                onChange={handleInputChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="DRAFT">임시 저장 (DRAFT)</option>
                <option value="ACTIVE">판매 중 (ACTIVE)</option>
                <option value="INACTIVE">판매 중지 (INACTIVE)</option>
              </select>
              <p className="mt-1 text-xs text-gray-500">
                상품의 판매 상태를 선택하세요.
              </p>
            </div>
          </div>
        </div>

        {/* 카테고리 선택 (Enum · 버튼) */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-xl font-semibold mb-4">카테고리</h2>
          <p className="text-sm text-gray-500 mb-3">선택한 카테고리는 버튼을 다시 눌러 해제할 수 있습니다.</p>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_TYPES.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                onClick={() => toggleCategory(value)}
                className={`px-4 py-2 rounded-lg font-medium transition ${
                  selectedCategoryTypes.includes(value)
                    ? 'bg-blue-500 text-white hover:bg-blue-600'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* 상품 변형(Variants) */}
        <div className="bg-white p-6 rounded-lg shadow">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-semibold">상품 변형</h2>
            <button
              type="button"
              onClick={() => setVariants([...variants, { sku: '', optionDisplay: '', price: '', stockQty: 0, active: true }])}
              className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600"
            >
              + 변형 추가
            </button>
          </div>

          {variants.length > 0 && (
            <div className="space-y-4">
              {variants.map((variant, index) => (
                <div key={variant.id || index} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-medium">변형 #{index + 1}</h3>
                    <button
                      type="button"
                      onClick={() => setVariants(variants.filter((_, i) => i !== index))}
                      className="px-3 py-1 bg-red-500 text-white rounded hover:bg-red-600 text-sm"
                    >
                      삭제
                    </button>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium mb-1">
                        SKU 코드 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="text"
                        value={variant.sku}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, sku: e.target.value } : v
                          ));
                        }}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="예: PROD-001-RED-L"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1">
                        옵션 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="text"
                        value={variant.optionDisplay ?? ''}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, optionDisplay: e.target.value } : v
                          ));
                        }}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="예: 색상: 빨강, 사이즈: L"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1">
                        가격 (선택)
                      </label>
                      <input
                        type="number"
                        value={variant.price}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, price: e.target.value } : v
                          ));
                        }}
                        min="0"
                        step="0.01"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="기본 가격 사용 시 비워두기"
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1">
                        재고 수량 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="number"
                        value={variant.stockQty}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, stockQty: parseInt(e.target.value) || 0 } : v
                          ));
                        }}
                        min="0"
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        required
                      />
                    </div>

                    <div className="col-span-2">
                      <label className="flex items-center">
                        <input
                          type="checkbox"
                          checked={variant.active}
                          onChange={(e) => {
                            setVariants(variants.map((v, i) =>
                              i === index ? { ...v, active: e.target.checked } : v
                            ));
                          }}
                          className="mr-2"
                        />
                        <span className="text-sm">활성화</span>
                      </label>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {variants.length === 0 && (
            <p className="text-sm text-gray-500">변형이 없으면 기본 상품만 판매됩니다.</p>
          )}
        </div>

        {/* 이미지 관리 */}
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-xl font-semibold mb-4">상품 이미지</h2>

          {/* 기존 이미지 */}
          {existingImages.length > 0 && (
            <div className="mb-6">
              <h3 className="text-sm font-medium mb-2 text-gray-600">
                기존 이미지 ({existingImages.length}개)
              </h3>
              <div className="grid grid-cols-4 gap-2">
                {existingImages.map((image) => (
                  <div key={image.uuid} className="relative group">
                    <img
                      src={image.url}
                      alt="Existing"
                      className="w-full aspect-square object-cover rounded border"
                    />
                    {image.primaryImage && (
                      <span className="absolute top-1 left-1 bg-blue-500 text-white text-xs px-2 py-1 rounded">
                        대표
                      </span>
                    )}
                    <button
                      type="button"
                      onClick={() => handleRemoveExistingImage(image.uuid)}
                      className="absolute top-1 right-1 bg-red-500 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 파일 선택 */}
          <div className="mb-4">
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              onChange={handleFileSelect}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
          </div>

          {/* 선택된 파일 목록 (업로드 전) */}
          {selectedFiles.length > 0 && (
            <div className="mb-4">
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm text-gray-600">
                  선택된 파일: {selectedFiles.length}개
                </span>
                <button
                  type="button"
                  onClick={handleUploadFiles}
                  disabled={isUploading}
                  className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50"
                >
                  {isUploading ? '업로드 중...' : '업로드'}
                </button>
              </div>
              <div className="grid grid-cols-4 gap-2">
                {selectedFiles.map((file, index) => (
                  <div key={index} className="relative">
                    <img
                      src={URL.createObjectURL(file)}
                      alt={file.name}
                      className="w-full aspect-square object-cover rounded border"
                    />
                    {uploadProgress[index] !== undefined && (
                      <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center rounded">
                        <span className="text-white text-sm">{uploadProgress[index]}%</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 업로드된 파일 목록 */}
          {uploadedFiles.length > 0 && (
            <div>
              <h3 className="text-sm font-medium mb-2 text-green-600">
                새로 업로드된 이미지 ({uploadedFiles.length}개)
              </h3>
              <div className="grid grid-cols-4 gap-2">
                {uploadedFiles.map((file, index) => (
                  <div key={index} className="relative group">
                    <img
                      src={file.url}
                      alt={`Uploaded ${index + 1}`}
                      className="w-full aspect-square object-cover rounded border"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveNewFile(index)}
                      className="absolute top-1 right-1 bg-red-500 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 최종 이미지 미리보기 */}
          {finalImages.length > 0 && (
            <div className="mt-6 pt-6 border-t">
              <h3 className="text-sm font-medium mb-2">
                최종 이미지 ({finalImages.length}개)
              </h3>
              <div className="grid grid-cols-4 gap-2">
                {finalImages.map((image, index) => (
                  <div key={image.uuid} className="relative">
                    <img
                      src={image.url}
                      alt={`Final ${index + 1}`}
                      className="w-full aspect-square object-cover rounded border"
                    />
                    {index === 0 && (
                      <span className="absolute top-1 left-1 bg-blue-500 text-white text-xs px-2 py-1 rounded">
                        대표
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 버튼 */}
        <div className="flex gap-4">
          <button
            type="button"
            onClick={() => navigate(`/shop/detail/${id}`)}
            className="px-6 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={isSubmitting || isUploading}
            className="flex-1 px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 disabled:opacity-50"
          >
            {isSubmitting ? '수정 중...' : '상품 수정'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ProductEditPage;

