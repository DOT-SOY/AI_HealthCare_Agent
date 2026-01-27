/**
 * 비밀번호 정책 검증 유틸리티
 *
 * 정책 규칙:
 * - 최소 길이: 8자 이상
 * - 다음 항목 중 2종 이상 포함:
 *   - 영문 대문자 (A-Z)
 *   - 영문 소문자 (a-z)
 *   - 숫자 (0-9)
 *   - 특수문자 (!@#$%^&* 등)
 */

/**
 * 비밀번호 정책 검증
 * @param {string} password - 검증할 비밀번호
 * @returns {{valid: boolean, message: string|null}} 검증 결과
 */
export const validatePassword = (password) => {
  // null 또는 undefined 체크
  if (!password || password.trim() === "") {
    return { valid: false, message: "비밀번호를 입력해주세요." };
  }

  // 최소 길이 검증
  if (password.length < 8) {
    return { valid: false, message: "비밀번호는 8자 이상이어야 합니다." };
  }

  // 문자 유형 검증
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasNumber = /[0-9]/.test(password);
  const hasSpecial = /[!@#$%^&*(),.?":{}|<>[\]\\/\-_+=~`;'<>]/.test(password);

  // 포함된 유형 개수 계산
  const typeCount = [hasUpper, hasLower, hasNumber, hasSpecial].filter(Boolean).length;

  // 2종 이상 포함 여부 검증
  if (typeCount < 2) {
    return {
      valid: false,
      message: "영문 대소문자/숫자/특수문자 중 2종 이상 포함해야 합니다."
    };
  }

  return { valid: true, message: null };
};

/**
 * 비밀번호 정책 안내 텍스트 반환
 * @returns {string} 정책 안내 텍스트
 */
export const getPasswordPolicyText = () => {
  return "• 8자 이상, 영문 대소문자/숫자/특수문자 중 2종 이상 포함";
};