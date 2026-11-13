import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunReactPreambleFix } from '../../vite-plugins/vite-qiankun-preamble';
import path from 'node:path';

const microAppName = 'whale-world';

const AGENT_PROXY = {
  '^/agent(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
};

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
    port: 8124,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    // 城中城民问话走网关的 /agent（whale-agent，网关不剥前缀）。
    // 内嵌基座时由基座 vite 代理转发；独立跑 8124 时必须自己代理，否则落到 SPA 回退 → 404/HTML。
    proxy: AGENT_PROXY,
  },
  // 产物预览（8125，e2e 跑这上面）同样要能打到网关，否则「在线问数」冒烟只能跑 dev
  preview: {
    port: 8125,
    proxy: AGENT_PROXY,
  },
  base: '/world',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
