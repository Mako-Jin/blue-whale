import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const lessVariables = fileURLToPath(
  new URL('./src/styles/variables.less', import.meta.url),
).replace(/\\/g, '/');

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
        additionalData: `@import "${lessVariables}";`,
      },
    },
  },
  server: {
    // 3100 是 common-tools 的 playground，3200 是日志控制台，这里用 3300
    port: 3300,
    host: true,
    /**
     * 把 /api 代理到后端。
     *
     * <p>走代理而不是让浏览器直接跨域，对**事件上报**尤其重要：
     * 跨域时 `navigator.sendBeacon` 会触发 CORS 预检，而预检在页面卸载阶段
     * 不可能完成 —— 结果是"页面关闭时那一批数据静默丢失"，
     * 而且丢在最需要它的时刻。同源则完全没有这个问题。
     */
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    target: 'es2022',
    chunkSizeWarningLimit: 1500,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
    },
  },
});
