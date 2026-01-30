import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import Button from '../../../components/common/Button';
import Card from '../../../components/common/Card';
import { createProduct } from '../../../services/productApi';
import { uploadFiles } from '../../../services/fileApi';
import { CATEGORY_TYPES } from '../../../constants/categoryTypes';

const ProductCreatePage = () => {
  const navigate = useNavigate();
  const loginState = useSelector((state) => state.loginSlice);

  useEffect(() => {
    if (!loginState?.roleNames?.includes('ADMIN')) {
      alert('접근 권한이 없습니다 (관리자 전용)');
      navigate('/shop/list', { replace: true });
    }
  }, [loginState, navigate]);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    basePrice: '',
    status: 'DRAFT', // 기본값: DRAFT
  });
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [isUploading, setIsUploading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [selectedCategoryTypes, setSelectedCategoryTypes] = useState([]);
  const [variants, setVariants] = useState([]);

  const toggleCategory = (value) => {
    setSelectedCategoryTypes((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
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

  const handleRemoveFile = (index) => {
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

      setUploadedFiles(results);
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

    if (uploadedFiles.length === 0) {
      setError('최소 1개 이상의 이미지를 업로드해주세요.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);

      const productData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        basePrice: parseFloat(formData.basePrice),
        status: formData.status || 'DRAFT',
        imageFilePaths: uploadedFiles.map(file => file.filePath),
        variants: variants.length > 0 ? variants.map(v => ({
          optionText: (v.optionDisplay ?? '').trim(),
          price: v.price ? parseFloat(v.price) : null,
          stockQty: parseInt(v.stockQty) || 0,
          active: v.active !== undefined ? v.active : true,
        })) : undefined,
        categoryTypes: selectedCategoryTypes.length > 0 ? selectedCategoryTypes : undefined,
      };

      const result = await createProduct(productData);
      navigate(`/shop/detail/${result.id}`);
    } catch (err) {
      setError(err.message || '상품 등록에 실패했습니다.');
      console.error('Create failed:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-token-6">
      <header className="section-header-token">
        <h1 className="section-title">상품 등록</h1>
        <p className="section-desc">보충제·헬스용품 등을 새로 등록합니다.</p>
      </header>

      {error && (
        <Card className="p-token-4 border border-red-500/40 text-red-400">
          {error}
        </Card>
      )}

      <form onSubmit={handleSubmit} className="space-y-token-6">
        {/* 기본 정보 */}
        <Card className="p-token-6 space-y-4">
          <h2 className="text-lg font-semibold text-text-main">기본 정보</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-2 text-text-main">
                상품명 <span className="text-red-400">*</span>
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
                상품 설명 <span className="text-red-400">*</span>
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
                  가격 <span className="text-red-400">*</span>
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
                  className="input-token w-full"
                >
                  <option value="DRAFT">임시 저장 (DRAFT)</option>
                  <option value="ACTIVE">판매 중 (ACTIVE)</option>
                  <option value="INACTIVE">판매 중지 (INACTIVE)</option>
                </select>
                <p className="mt-1 text-xs text-text-muted">
                  상품의 판매 상태를 선택하세요. 기본값은 임시 저장입니다.
                </p>
              </div>
            </div>
          </div>
        </Card>

        {/* 카테고리 선택 (Enum · 버튼) */}
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
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() =>
                setVariants([...variants, { optionDisplay: '', price: '', stockQty: 0, active: true }])
              }
            >
              + 변형 추가
            </Button>
          </div>

          {variants.length > 0 && (
            <div className="space-y-4">
              {variants.map((variant, index) => (
                <Card key={index} className="p-token-4 border border-border-default/70">
                  <div className="flex items-center justify-between mb-3">
                    <h3 className="font-medium text-text-main">변형 #{index + 1}</h3>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="text-red-400"
                      onClick={() => setVariants(variants.filter((_, i) => i !== index))}
                    >
                      삭제
                    </Button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium mb-1 text-text-main">
                        옵션 <span className="text-red-400">*</span>
                      </label>
                      <input
                        type="text"
                        value={variant.optionDisplay ?? ''}
                        onChange={(e) => {
                          setVariants(
                            variants.map((v, i) =>
                              i === index ? { ...v, optionDisplay: e.target.value } : v,
                            ),
                          );
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
                          setVariants(
                            variants.map((v, i) =>
                              i === index ? { ...v, price: e.target.value } : v,
                            ),
                          );
                        }}
                        min="0"
                        step="0.01"
                        className="input-token w-full"
                        placeholder="기본 가격 사용 시 비워두기"
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium mb-1 text-text-main">
                        재고 수량 <span className="text-red-400">*</span>
                      </label>
                      <input
                        type="number"
                        value={variant.stockQty}
                        onChange={(e) => {
                          setVariants(
                            variants.map((v, i) =>
                              i === index
                                ? { ...v, stockQty: parseInt(e.target.value, 10) || 0 }
                                : v,
                            ),
                          );
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
                          setVariants(
                            variants.map((v, i) =>
                              i === index ? { ...v, active: e.target.checked } : v,
                            ),
                          );
                        }}
                      />
                      <label htmlFor={`active-${index}`} className="text-sm text-text-main cursor-pointer">
                        활성화
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

        {/* 이미지 업로드 */}
        <Card className="p-token-6 space-y-4">
          <h2 className="text-lg font-semibold text-text-main">상품 이미지</h2>

          {/* 파일 선택 */}
          <div>
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              onChange={handleFileSelect}
              className="block w-full text-sm text-text-muted file:mr-4 file:py-2 file:px-4 file:rounded-token file:border-0 file:text-sm file:font-medium file:bg-primary-500/10 file:text-primary-500 hover:file:bg-primary-500/20"
            />
          </div>

          {/* 선택된 파일 목록 (업로드 전) */}
          {selectedFiles.length > 0 && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-text-muted">
                  선택된 파일: {selectedFiles.length}개
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

          {/* 업로드된 파일 목록 */}
          {uploadedFiles.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-primary-500">
                업로드 완료: {uploadedFiles.length}개
              </h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {uploadedFiles.map((file, index) => (
                  <div key={index} className="relative group">
                    <img
                      src={file.url}
                      alt={`Uploaded ${index + 1}`}
                      className="w-full aspect-square object-cover rounded-token border border-border-default"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveFile(index)}
                      className="absolute top-1 right-1 bg-red-500 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>

        {/* 버튼 */}
        <div className="flex gap-4 justify-end">
          <Button
            type="button"
            variant="ghost"
            size="md"
            onClick={() => navigate('/shop/list')}
          >
            취소
          </Button>
          <Button
            type="submit"
            size="md"
            className="min-w-[160px]"
            disabled={isSubmitting || isUploading}
          >
            {isSubmitting ? '등록 중...' : '상품 등록'}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default ProductCreatePage;

