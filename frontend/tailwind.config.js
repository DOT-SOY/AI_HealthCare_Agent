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
      },
      colors: {
        'neon-green': '#00ff41',
        /* 디자인 토큰: Tailwind 유틸리티(bg-bg-root, text-primary-500 등)용 */
        'bg-root': 'var(--bg-root)',
        'bg-surface': 'var(--bg-surface)',
        'bg-card': 'var(--bg-card)',
        'primary': {
          500: 'var(--primary-500)',
          400: 'var(--primary-400)',
          glow: 'var(--primary-glow)',
        },
        'text-main': 'var(--text-main)',
        'text-sub': 'var(--text-sub)',
        'text-muted': 'var(--text-muted)',
        'border-default': 'var(--border-default)',
      },
      boxShadow: {
        'card': '0 4px 6px -1px rgba(0, 0, 0, 0.3), 0 2px 4px -2px rgba(0, 0, 0, 0.2)',
        'card-hover': '0 10px 15px -3px rgba(0, 0, 0, 0.35), 0 4px 6px -4px rgba(0, 0, 0, 0.2)',
        'glow': '0 0 20px var(--primary-glow)',
      },
    },
  },
  plugins: [],
}


