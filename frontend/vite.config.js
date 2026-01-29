import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    proxy: {
      // localhost 전용
      '/tosspayments-proxy': {
        target: 'https://js.tosspayments.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/tosspayments-proxy/, ''),
      },
       // Backend API
       '/api': {
         target: 'http://localhost:8080',
         changeOrigin: true,
       },

       // WebSocket
       '/ws': {
         target: 'ws://localhost:8080',
         ws: true,
       },
    },
  },
})
