import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // 将体积较大的第三方库拆成独立 chunk：利于浏览器并行下载与长期缓存，
        // 配合页面组件按需异步加载，首屏不再一次性加载全部依赖。
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          echarts: ['echarts'],
          vue: ['vue']
        }
      }
    }
  },
  server: {
    host: '127.0.0.1',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});
