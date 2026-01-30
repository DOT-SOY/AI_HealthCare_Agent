/**
 * 공통 Button 컴포넌트 (토큰 기반)
 * - primary: neon lime + hover glow
 * - ghost: dark bg + lime border
 * - 가격/CTA는 무조건 primary 컬러만 사용하므로 CTA용은 variant="primary"
 */
const variantClasses = {
  primary:
    'bg-primary-500 text-bg-root font-medium rounded-lg hover:shadow-glow transition-shadow focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2 focus:ring-offset-bg-surface disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:shadow-none',
  ghost:
    'bg-bg-card text-primary-500 border-2 border-primary-500 font-medium rounded-lg hover:bg-primary-500/10 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-400 focus:ring-offset-2 focus:ring-offset-bg-surface disabled:opacity-50 disabled:cursor-not-allowed',
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
