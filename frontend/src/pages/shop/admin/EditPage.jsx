import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import Button from '../../../components/common/Button';
import Card from '../../../components/common/Card';
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

      if (data.categories && data.categories.length > 0) {
        setSelectedCategoryTypes(data.categories.map((c) => c.categoryType).filter(Boolean));
      }

      if (data.variants && data.variants.length > 0) {
        setVariants(data.variants.map(v => ({
          id: v.id,
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

      const progress = {};
      selectedFiles.forEach((_, index) => {
        progress[index] = 0;
      });
      setUploadProgress(progress);

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
      setSelectedFiles([]);
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

      const existingFilePaths = existingImages
        .filter(img => img.filePath)
        .map(img => img.filePath);

      const allImageFilePaths = [
        ...existingFilePaths,
        ...uploadedFiles.map(file => file.filePath),
      ];

      const variantsPayload = variants.map(v => ({
        id: v.id != null ? v.id : undefined,
        optionText: (v.optionDisplay ?? '').trim() || '기본 옵션',
        price: v.price !== '' && v.price != null ? parseFloat(v.price) : null,
        stockQty: v.stockQty != null ? Number(v.stockQty) : 0,
        active: v.active !== undefined ? v.active : true,
      }));

      const productData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        basePrice: parseFloat(formData.basePrice),
        status: formData.status,
        imageFilePaths: allImageFilePaths.length > 0 ? allImageFilePaths : [],
        variants: variantsPayload,
        categoryTypes: selectedCategoryTypes.length > 0 ? selectedCategoryTypes : [],
      };

      console.log('[EditPage] 전송할 productData:', JSON.stringify(productData, null, 2));

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
      <div className="flex justify-center items-center min-h-[400px] text-text-main">
        <p className="text-text-sub">로딩 중...</p>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex flex-col justify-center items-center min-h-[400px] gap-4 text-text-main">
        <p>상품을 찾을 수 없습니다.</p>
        <Button variant="ghost" size="md" onClick={() => navigate('/shop/list')}>
          목록으로 돌아가기
        </Button>
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
    <div className="w-full max-w-4xl mx-auto space-y-token-6 text-text-main">
      <header className="section-header-token">
        <h1 className="section-title">상품 수정</h1>
        <p className="section-desc">등록된 상품 정보를 수정합니다. 변경 후 저장하면 상품 상세에 반영됩니다.</p>
      </header>

      {error && (
        <Card className="p-token-4 border border-accent-secondary/50 text-accent-secondary bg-accent-secondary/10">
          {error}
        </Card>
      )}

      <form onSubmit={handleSubmit} className="space-y-token-6">
        <Card className="p-token-6 space-y-4">
          <h2 className="text-lg font-semibold text-text-main">기본 정보</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-2 text-text-main">
                상품명 <span className="text-accent-secondary">*</span>
              </label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleInputChange}
                required
                className="input-token w-full"
                placeholder="상품명을 입력하세요"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-2 text-text-main">
                상품 설명 <span className="text-accent-secondary">*</span>
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleInputChange}
                required
                rows={5}
                className="input-token w-full min-h-[140px] resize-y"
                placeholder="상품 설명을 입력하세요"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-2 text-text-main">
                  가격 <span className="text-accent-secondary">*</span>
                </label>
                <input
                  type="number"
                  name="basePrice"
                  value={formData.basePrice}
                  onChange={handleInputChange}
                  required
                  min="0"
                  step="0.01"
                  className="input-token w-full"
                  placeholder="0"
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 text-text-main">
                  판매 상태
                </label>
                <select
                  name="status"
                  value={formData.status}
                  onChange={handleInputChange}
                  className="select-token w-full"
                >
                  <option value="DRAFT">임시 저장 (DRAFT)</option>
                  <option value="ACTIVE">판매 중 (ACTIVE)</option>
                  <option value="INACTIVE">판매 중지 (INACTIVE)</option>
                </select>
                <p className="mt-1 text-xs text-text-muted">
                  상품의 판매 상태를 선택하세요.
                </p>
              </div>
            </div>
          </div>
        </Card>

        <Card className="p-token-6 space-y-3">
          <h2 className="text-lg font-semibold text-text-main">카테고리</h2>
          <p className="text-sm text-text-muted">
            선택한 카테고리는 버튼을 다시 눌러 해제할 수 있습니다.
          </p>
          <div className="flex flex-wrap gap-2">
            {CATEGORY_TYPES.map(({ value, label }) => {
              const isSelected = selectedCategoryTypes.includes(value);
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => toggleCategory(value)}
                  className={`segment-btn ${isSelected ? 'segment-btn-active' : ''}`}
                >
                  <span>{label}</span>
                </button>
              );
            })}
          </div>
        </Card>

        {/* 상품 변형(Variants) */}
        <Card className="p-token-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-main">상품 변형</h2>
            <button
              type="button"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setVariants((prev) => [...prev, { optionDisplay: '', price: '', stockQty: 0, active: true }]);
              }}
              className="inline-flex items-center justify-center px-3 py-1.5 text-sm bg-gray-default text-text-main border border-gray-default font-medium rounded-token hover:border-primary-500 hover:text-primary-500 hover:bg-primary-500/10"
            >
              + 변형 추가
            </button>
          </div>

          {variants.length > 0 && (
            <div className="space-y-4">
              {variants.map((variant, index) => (
                <Card key={variant.id ?? index} className="p-token-4 border border-border-default/70">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-medium text-text-main">변형 #{index + 1}</h3>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.preventDefault();
                        setVariants((prev) => prev.filter((_, i) => i !== index));
                      }}
                      className="inline-flex items-center justify-center px-3 py-1.5 text-sm text-accent-secondary border border-transparent rounded-token hover:bg-accent-secondary/10"
                    >
                      삭제
                    </button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium mb-1 text-text-main">
                        옵션 <span className="text-accent-secondary">*</span>
                      </label>
                      <input
                        type="text"
                        value={variant.optionDisplay ?? ''}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, optionDisplay: e.target.value } : v
                          ));
                        }}
                        className="input-token w-full"
                        placeholder="예: 색상: 빨강, 사이즈: L"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1 text-text-main">
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
                        className="input-token w-full"
                        placeholder="기본 가격 사용 시 비워두기"
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1 text-text-main">
                        재고 수량 <span className="text-accent-secondary">*</span>
                      </label>
                      <input
                        type="number"
                        value={variant.stockQty}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, stockQty: parseInt(e.target.value, 10) || 0 } : v
                          ));
                        }}
                        min="0"
                        className="input-token w-full"
                        required
                      />
                    </div>

                    <div className="sm:col-span-2 flex items-center gap-2">
                      <input
                        type="checkbox"
                        id={`active-${index}`}
                        checked={variant.active}
                        onChange={(e) => {
                          setVariants(variants.map((v, i) =>
                            i === index ? { ...v, active: e.target.checked } : v
                          ));
                        }}
                        className="rounded border-border-default text-primary-500 focus:ring-primary-500"
                      />
                      <label htmlFor={`active-${index}`} className="text-sm text-text-main cursor-pointer">
                        활성화 (판매 노출)
                      </label>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}

          {variants.length === 0 && (
            <p className="text-sm text-text-muted">변형이 없으면 기본 상품만 판매됩니다.</p>
          )}
        </Card>

        <Card className="p-token-6 space-y-4">
          <h2 className="text-lg font-semibold text-text-main">상품 이미지</h2>

          {existingImages.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-text-sub">
                기존 이미지 ({existingImages.length}개) — 제거할 이미지는 × 버튼으로 삭제
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {existingImages.map((image) => (
                  <div key={image.uuid} className="relative group">
                    <img
                      src={image.url}
                      alt="기존"
                      className="w-full aspect-square object-cover rounded-token border border-border-default"
                    />
                    {image.primaryImage && (
                      <span className="absolute top-1 left-1 bg-primary-500 text-bg-root text-xs px-2 py-1 rounded-token font-medium">
                        대표
                      </span>
                    )}
                    <button
                      type="button"
                      onClick={() => handleRemoveExistingImage(image.uuid)}
                      className="absolute top-1 right-1 bg-accent-secondary text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition text-lg leading-none"
                      aria-label="이미지 제거"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-text-main mb-1">새 이미지 추가</label>
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              onChange={handleFileSelect}
              className="block w-full text-sm text-text-muted file:mr-4 file:py-2 file:px-4 file:rounded-token file:border-0 file:text-sm file:font-medium file:bg-primary-500/10 file:text-primary-500 hover:file:bg-primary-500/20"
            />
          </div>

          {selectedFiles.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-text-muted">
                  선택된 파일: {selectedFiles.length}개 — 업로드 버튼을 눌러 서버에 저장하세요
                </span>
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  onClick={handleUploadFiles}
                  disabled={isUploading}
                >
                  {isUploading ? '업로드 중...' : '업로드'}
                </Button>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {selectedFiles.map((file, index) => (
                  <div key={index} className="relative">
                    <img
                      src={URL.createObjectURL(file)}
                      alt={file.name}
                      className="w-full aspect-square object-cover rounded-token border border-border-default"
                    />
                    {uploadProgress[index] !== undefined && (
                      <div className="absolute inset-0 bg-black/50 flex items-center justify-center rounded-token">
                        <span className="text-white text-sm">{uploadProgress[index]}%</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {uploadedFiles.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-primary-500">
                새로 업로드된 이미지 ({uploadedFiles.length}개)
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {uploadedFiles.map((file, index) => (
                  <div key={index} className="relative group">
                    <img
                      src={file.url}
                      alt={`업로드 ${index + 1}`}
                      className="w-full aspect-square object-cover rounded-token border border-border-default"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveNewFile(index)}
                      className="absolute top-1 right-1 bg-accent-secondary text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition text-lg leading-none"
                      aria-label="이미지 제거"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {finalImages.length > 0 && (
            <div className="pt-4 border-t border-border-default">
              <h3 className="text-sm font-medium text-text-main mb-2">
                최종 이미지 순서 ({finalImages.length}개) — 저장 시 이 순서로 반영됩니다
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {finalImages.map((image, index) => (
                  <div key={image.uuid} className="relative">
                    <img
                      src={image.url}
                      alt={`최종 ${index + 1}`}
                      className="w-full aspect-square object-cover rounded-token border border-border-default"
                    />
                    {index === 0 && (
                      <span className="absolute top-1 left-1 bg-primary-500 text-bg-root text-xs px-2 py-1 rounded-token font-medium">
                        대표
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>

        <div className="flex gap-4 justify-end">
          <Button
            type="button"
            variant="ghost"
            size="md"
            onClick={() => navigate(`/shop/detail/${id}`)}
          >
            취소
          </Button>
          <Button
            type="submit"
            size="md"
            className="min-w-[160px]"
            disabled={isSubmitting || isUploading}
          >
            {isSubmitting ? '수정 중...' : '상품 수정'}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default ProductEditPage;

