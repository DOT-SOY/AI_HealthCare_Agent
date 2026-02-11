import jwtAxios from "../util/jwtUtil";

const BASE_URL = "/member-body-info";

/**
 * 인바디 이미지 업로드 → OCR 분석 결과 반환 (저장 X)
 * @param {File} file
 * @returns {Promise<object>} MemberInfoBodyDTO (분석 결과)
 */
export const analyzeInbodyImage = async (file) => {
  const form = new FormData();
  form.append("file", file);

  // 백엔드 경로 변경됨: /analyze (저장 안함)
  const res = await jwtAxios.post(`${BASE_URL}/ocr/analyze`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return res.data;
};

/**
 * 검증된 인바디 정보를 저장 (기존 생성 API 재활용)
 * @param {object} data MemberInfoBodyDTO
 */
export const saveVerifiedBodyInfo = async (data) => {
  console.log("[OCR API] 저장 요청 시작:", data);
  try {
    // member-body-info 엔드포인트에 POST하면 생성됨
    const res = await jwtAxios.post(BASE_URL, data);
    console.log("[OCR API] 저장 성공 - 응답 상태:", res.status, "응답 데이터:", res.data);
    return res.data;
  } catch (error) {
    console.error("[OCR API] 저장 실패:", error);
    console.error("[OCR API] 에러 응답:", error.response?.data);
    console.error("[OCR API] 에러 상태 코드:", error.response?.status);
    throw error;
  }
};
