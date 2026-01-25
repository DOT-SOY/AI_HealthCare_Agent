import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getProduct, updateProduct } from '../../../services/productApi';
import { uploadFiles } from '../../../services/fileApi';

const ProductEditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    basePrice: '',
  });
  const [existingImages, setExistingImages] = useState([]);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState({});
  const [isUploading, setIsUploading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadProduct();
  }, [id]);

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
      });
      setExistingImages(data.images || []);
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
        imageFilePaths: allImageFilePaths.length > 0 ? allImageFilePaths : [],
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
          </div>
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

