import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // localhost 전용
      '/tosspayments-proxy': {
        target: 'https://js.tosspayments.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/tosspayments-proxy/, ''),
      },
    },
  },
})
