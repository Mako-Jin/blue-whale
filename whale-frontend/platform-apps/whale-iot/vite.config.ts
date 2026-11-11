import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunReactPreambleFix } from '../../vite-plugins/vite-qiankun-preamble';
import path from 'node:path';

const microAppName = 'whale-iot';

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
    // 8115/8116 已被 whale-lms/whale-party 占用，IoT 前端自 Wave 6.02 M3 起用 8121
    port: 8121,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    // 独立运行（非基座内嵌）时，把 /iot 前缀请求转发到网关，复用鉴权与 internal-deny 链路
    proxy: {
      '^/iot(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  base: '/iot',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
