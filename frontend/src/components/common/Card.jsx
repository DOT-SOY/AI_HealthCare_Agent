/**
 * 공통 Card 컴포넌트
 * - 규칙: dark surface + soft shadow
 * - 색상/간격/그림자는 전역 토큰만 사용 (하드코딩 금지)
 */
const Card = ({ children, className = '', as: Component = 'div', ...rest }) => {
  return (
    <Component
      className={`bg-bg-card text-text-main rounded-lg shadow-card border border-border-default ${className}`.trim()}
      {...rest}
    >
      {children}
    </Component>
  );
};

export default Card;
