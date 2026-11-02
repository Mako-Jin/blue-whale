import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 页面级单测：jsdom + antd 真渲染，API 层统一 vi.mock（见各 *.test.tsx）
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // antd 全量渲染在 jsdom 下单用例可达数秒，默认 5s 过紧
    testTimeout: 15000,
  },
});
