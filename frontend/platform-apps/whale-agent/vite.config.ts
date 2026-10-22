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
    port: 8004,
    proxy: {
      // 开发期把 /auth（登录续期）与 /agent（问答/会话/索引）转发到网关 8080，复用网关鉴权链路。
      // 正则用 (/|$) 同时匹配「前缀即完整路径」的接口（如 POST /agent/chat），否则会落到 vite 自身 404。
      '^/(auth|agent)(/|$)': {
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
