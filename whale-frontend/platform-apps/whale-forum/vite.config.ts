import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunReactPreambleFix } from '../../vite-plugins/vite-qiankun-preamble';
import path from 'node:path';

const microAppName = 'whale-forum';

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
    // 端口唯一权威清单见 docs/service-catalog.md §七（qiankun 子应用区段）
    // 8123 = 论坛（Wave 6.43；8122 已被 whale-twin 占用）
    port: 8123,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    // 独立运行（非基座内嵌）时，把 /forum 与 /file 前缀请求转发到网关：
    // /forum 走论坛服务；/file 走文件服务（图片/附件）
    proxy: {
      '^/forum(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '^/file(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  base: '/forum',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
