import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: 'localhost', // 改为 localhost 避免权限问题
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8001', // 后端地址 (已改为8001)
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 去掉 /api 前缀
      },
      '/static': {
        target: 'http://127.0.0.1:8001',
        changeOrigin: true
      }
    }
  }
})
