/**
 * 기본 스타일 컴포넌트
 * 각 컴포넌트에서 필요한 경우 이 컴포넌트를 사용하여 기본 스타일을 적용
 */
const BaseStyles = ({ children, className = '' }) => {
  return (
    <div className={`box-border ${className}`} style={{ boxSizing: 'border-box' }}>
      {children}
    </div>
  );
};

export default BaseStyles;

