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
    // 3100 已被 common-tools 的 playground 占用，这里用 3200
    port: 3200,
    host: true,
    /**
     * 把 /api 代理到后端。
     *
     * 走代理而不是让浏览器直接跨域：开发期请求与服务端同源，
     * 因此 Cookie、CORS 预检、Referer 这些跨域才会出现的问题一个都不会分散注意力 ——
     * 排查日志功能时被跨域问题挡住是最不划算的事。
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
