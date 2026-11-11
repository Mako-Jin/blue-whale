import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunReactPreambleFix } from '../../vite-plugins/vite-qiankun-preamble';
import path from 'node:path';

// qiankun 子应用名称，需与基座 apps.ts 中 name 一致
const microAppName = 'whale-portal';

export default defineConfig({
  plugins: [
    react(),
    qiankun(microAppName, {
      useDevMode: true,
    }),
    // qiankun 内嵌时把 react-refresh 预导改写为经典脚本（仅 dev），详见 vite-plugins/vite-qiankun-preamble.ts
    qiankunReactPreambleFix(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8101,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
  },
  // 子应用 base 与基座 activeRule 保持一致
  base: '/portal',
  build: {
    target: 'esnext',
    sourcemap: false,
    // 避免打包时出现资源路径问题
    rollupOptions: {
      output: {
        format: 'es',
      },
    },
  },
});
