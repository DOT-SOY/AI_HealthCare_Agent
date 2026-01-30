/**
 * 공통 Button 컴포넌트 (토큰 기반)
 * - primary: 내부 채운 네온그린 (정말 중요한 CTA만: 검색, 장바구니 담기 등)
 * - ghost: 기본 회색조 → 호버 시 네온그린 (일반 버튼)
 */
const variantClasses = {
  primary:
    'bg-primary-500 text-bg-root font-emphasis font-medium rounded-token border border-primary-500 hover:shadow-glow transition-all duration-200 ease-out-quart focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2 focus:ring-offset-bg-surface disabled:bg-gray-default disabled:text-text-sub disabled:border-gray-default disabled:hover:shadow-none disabled:cursor-not-allowed',
  ghost:
    'bg-gray-default text-text-main border border-gray-default font-medium rounded-token hover:border-primary-500 hover:text-primary-500 hover:bg-primary-500/10 transition-all duration-200 ease-out-quart focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2 focus:ring-offset-bg-surface disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-gray-default disabled:text-text-sub disabled:border-gray-default disabled:hover:bg-gray-default disabled:hover:border-gray-default disabled:hover:text-text-sub',
};

const sizeClasses = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-base',
  lg: 'px-6 py-3 text-lg',
};

const Button = ({
  children,
  variant = 'primary',
  size = 'md',
  type = 'button',
  className = '',
  disabled = false,
  as: Component = 'button',
  ...rest
}) => {
  const base = 'inline-flex items-center justify-center';
  const variantClass = variantClasses[variant] ?? variantClasses.primary;
  const sizeClass = sizeClasses[size] ?? sizeClasses.md;

  return (
    <Component
      type={Component === 'button' ? type : undefined}
      disabled={disabled}
      className={`${base} ${sizeClass} ${variantClass} ${className}`.trim()}
      {...rest}
    >
      {children}
    </Component>
  );
};

export default Button;
export { variantClasses, sizeClasses };
