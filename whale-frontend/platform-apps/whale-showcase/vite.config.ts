import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

/**
 * whale-showcase：三维展示场景。
 *
 * <p>与 whale-world（蓝鲸城）是**两套东西**：这里不挂 qiankun、不连后端、不接鉴权，
 * 目标是"能出图"的纯展示站点。所以 base 用 '/'，dev 直开 8126 即可看到画面。</p>
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8126,
    cors: true,
  },
  preview: {
    port: 8127,
  },
  base: '/',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
