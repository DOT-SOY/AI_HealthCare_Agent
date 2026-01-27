import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * JWT 토큰이 자동으로 포함되는 axios 인스턴스
 */
const jwtAxios = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
  },
});

// 요청 인터셉터: JWT 토큰을 자동으로 헤더에 추가
jwtAxios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터: 에러 처리
jwtAxios.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // AbortError는 그대로 전달 (호출자가 처리)
    if (error.name === 'AbortError' || error.code === 'ERR_CANCELED' || axios.isCancel(error)) {
      throw error;
    }
    
    // axios 에러 응답 처리
    if (error.response) {
      const errorData = error.response.data || {};
      const errorMessage = errorData.message || `HTTP error! status: ${error.response.status}`;
      const customError = new Error(errorMessage);
      customError.response = error.response;
      throw customError;
    }
    
    console.error('API Error:', error);
    throw error;
  }
);

export default jwtAxios;
