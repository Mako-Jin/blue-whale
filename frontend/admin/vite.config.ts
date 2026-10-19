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
    port: 8001,
    proxy: {
      // 开发期将 whale-auth 各一级前缀转发到网关（8080），复用网关鉴权/黑名单校验链路
      '^/(auth|user|dept|role|menu|permission|audit)/': {
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
