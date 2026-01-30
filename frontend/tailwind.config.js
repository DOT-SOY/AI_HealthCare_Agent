/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['var(--font-sans)', 'system-ui', 'sans-serif'],
        emphasis: ['var(--font-emphasis)', 'var(--font-sans)', 'sans-serif'],
        display: ['var(--font-display)', 'var(--font-sans)', 'sans-serif'],
      },
      borderRadius: {
        'token-sm': '8px',
        'token': '12px',
        'token-lg': '15px',
      },
      colors: {
        'neon-green': '#00ff41',
        'bg-root': 'var(--bg-root)',
        'bg-surface': 'var(--bg-surface)',
        'bg-card': 'var(--bg-card)',
        'primary': {
          500: 'var(--primary-500)',
          400: 'var(--primary-400)',
          600: 'var(--primary-600)',
          glow: 'var(--primary-glow)',
        },
        'accent-secondary': 'var(--accent-secondary)',
        'accent-orange': 'var(--accent-orange)',
        'gray-100': 'var(--gray-100)',
        'gray-200': 'var(--gray-200)',
        'gray-300': 'var(--gray-300)',
        'text-main': 'var(--text-main)',
        'text-sub': 'var(--text-sub)',
        'text-muted': 'var(--text-muted)',
        'border-default': 'var(--border-default)',
        'gray-default': 'var(--gray-default)',
        /* member/폼 호환: shop 토큰과 동일 (data-theme="dark" 시 동작) */
        'baseBg': 'var(--bg-root)',
        'baseSurface': 'var(--bg-surface)',
        'baseMuted': 'var(--text-muted)',
        'baseBorder': 'var(--border-default)',
      },
      boxShadow: {
        'card': '0 4px 6px -1px rgba(0, 0, 0, 0.35), 0 2px 4px -2px rgba(0, 0, 0, 0.2)',
        'card-hover': '0 8px 16px -4px rgba(0, 0, 0, 0.4), 0 0 14px rgba(182, 255, 0, 0.7), 0 0 20px rgba(182, 255, 0, 0.35)',
        'glow': '0 0 32px var(--primary-glow)',
        'glow-sm': '0 0 16px var(--primary-glow)',
      },
      transitionTimingFunction: {
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
        'out-quart': 'cubic-bezier(0.25, 1, 0.5, 1)',
      },
      keyframes: {
        'stagger-in': {
          '0%': { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'energy-bar': {
          '0%': { transform: 'scaleX(0)', opacity: '1' },
          '100%': { transform: 'scaleX(1)', opacity: '1' },
        },
        'pulse-glow': {
          '0%, 100%': { boxShadow: '0 0 20px var(--primary-glow)' },
          '50%': { boxShadow: '0 0 28px var(--primary-glow)' },
        },
      },
      animation: {
        'stagger-in': 'stagger-in 0.5s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1)) forwards',
        'energy-bar': 'energy-bar 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards',
        'pulse-glow': 'pulse-glow 1.5s ease-in-out infinite',
      },
      spacing: {
        'token-2': 'var(--spacing-token-2)',
        'token-4': 'var(--spacing-token-4)',
        'token-6': 'var(--spacing-token-6)',
        'token-8': 'var(--spacing-token-8)',
        'section': 'var(--gap-section)',
      },
      gap: {
        'grid': 'var(--gap-grid)',
        'section': 'var(--gap-section)',
      },
    },
  },
  plugins: [],
}


