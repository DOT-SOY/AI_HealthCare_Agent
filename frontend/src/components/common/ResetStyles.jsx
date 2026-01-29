/**
 * Tailwind 기반 리셋 래퍼 컴포넌트
 * - 별도 CSS 파일 없이 Tailwind 유틸리티만 사용
 * - 박스 사이즈를 border-box로 고정하고, 추가 스타일은 props로 전달
 */

const ResetStyles = ({ children, className = '' }) => {
  return (
    <div className={`box-border ${className}`}>
      {children}
    </div>
  );
};

export default ResetStyles;

