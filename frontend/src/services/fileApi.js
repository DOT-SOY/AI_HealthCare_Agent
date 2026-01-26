import fetchAPI from './api';

const API_BASE_URL = 'http://localhost:8080/api';

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

  const url = `${API_BASE_URL}/files/upload`;

  // JWT 토큰이 있으면 Authorization 헤더 추가
  const headers = {};
  const token = localStorage.getItem('accessToken');
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    // 진행률 추적
    if (onProgress) {
      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
          const percentComplete = Math.round((e.loaded / e.total) * 100);
          onProgress(percentComplete);
        }
      });
    }

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText);
          resolve(response);
        } catch (error) {
          reject(new Error('Invalid JSON response'));
        }
      } else {
        try {
          const errorData = JSON.parse(xhr.responseText);
          reject(new Error(errorData.message || `HTTP error! status: ${xhr.status}`));
        } catch {
          reject(new Error(`HTTP error! status: ${xhr.status}`));
        }
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Network error'));
    });

    xhr.addEventListener('abort', () => {
      reject(new Error('Upload aborted'));
    });

    xhr.open('POST', url);
    
    // Authorization 헤더 설정
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }

    xhr.send(formData);
  });
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

