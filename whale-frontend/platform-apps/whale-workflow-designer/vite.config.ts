import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8003,
    proxy: {
      // 开发期将 whale-service-auth（/auth）、whale-workflow（/workflow）、whale-form（/form）与 whale-file（/file）前缀转发到网关（8080），复用网关鉴权链路
      // 注意：正则用 (/|$) 同时匹配「前缀即完整路径」的接口（如 POST /form/data），否则会落到 vite 自身 404
      '^/(auth|workflow|form|file)(/|$)': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
