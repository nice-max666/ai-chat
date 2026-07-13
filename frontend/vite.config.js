// frontend/vite.config.js
import { defineConfig } from 'vite'    // 1. 引入 defineConfig
import vue from '@vitejs/plugin-vue'   // 2. 引入 vue 插件
import path from 'path'                // 3. 引入 path 模块

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/assistant': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
