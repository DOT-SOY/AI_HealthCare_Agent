import jwtAxios from './jwtAxios';

/**
 * 파일 업로드 API
 * @param {File} file - 업로드할 파일
 * @param {string} directory - 저장할 디렉토리 (기본값: "products")
 * @param {Function} onProgress - 진행률 콜백 (0-100)
 * @returns {Promise<{filePath: string, url: string, fileSize: number, contentType: string}>}
 */
export const uploadFile = async (file, directory = 'products', onProgress = null) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('directory', directory);

  const config = {
    // FormData 사용 시 Content-Type은 axios가 자동으로 설정 (multipart/form-data + boundary)
    headers: {
      'Content-Type': undefined, // axios가 자동으로 설정하도록
    },
    // 진행률 추적
    ...(onProgress && {
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percentComplete = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
          onProgress(percentComplete);
        }
      },
    }),
  };

  const response = await jwtAxios.post('/files/upload', formData, config);
  return response.data;
};

/**
 * 여러 파일을 순차적으로 업로드
 * @param {File[]} files - 업로드할 파일 배열
 * @param {string} directory - 저장할 디렉토리
 * @param {Function} onFileProgress - 파일별 진행률 콜백 (fileIndex, percent)
 * @returns {Promise<Array<{filePath: string, url: string}>>}
 */
export const uploadFiles = async (files, directory = 'products', onFileProgress = null) => {
  const results = [];

  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    const result = await uploadFile(
      file,
      directory,
      (percent) => {
        if (onFileProgress) {
          onFileProgress(i, percent);
        }
      }
    );
    results.push(result);
  }

  return results;
};

