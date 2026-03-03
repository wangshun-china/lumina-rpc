import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    open: true,
    proxy: {
      // 所有 /api/v1 开头的请求发往 Control Plane (8080)
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
      // 消费者命令相关的请求发往 Command 服务 (8083)
      '/api/command': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        secure: false,
        ws: true,
      },
    },
  },
  optimizeDeps: {
    include: ['@vue-flow/core', '@vue-flow/controls', '@vue-flow/minimap', '@vue-flow/background'],
  },
})