import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(import.meta.dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 使用 /share/ 前缀精确匹配分享链接路径
      '/share/': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})