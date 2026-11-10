import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

const microAppName = 'whale-assistant';

export default defineConfig({
  plugins: [
    react(),
    qiankun(microAppName, {
      useDevMode: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    // 端口唯一权威清单见 docs/service-catalog.md §七（qiankun 子应用区段）
    // 8117 已被 whale-mail 占用（Wave 5.89），assistant 自 Wave 6.01 M3 起改用 8120
    port: 8120,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
  },
  base: '/assistant',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
