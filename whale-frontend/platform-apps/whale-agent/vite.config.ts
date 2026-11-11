import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

// 微应用名必须与基座 apps.ts 的 `name` 一致（qiankun 按 name 判定生命周期归属）
const microAppName = 'whale-agent';

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
    // 端口权威清单见 docs/service-catalog.md §七：8004 为知识助手独立运行端口，
    // 基座内嵌时 entry 由 VITE_SUBAPP_ENTRY_AGENT 注入（回退 //localhost:8004）
    port: 8004,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    proxy: {
      // 只代理接口段。`/agent` 同时是子应用资源前缀（base: '/agent'），
      // 若写成 '^/agent(/|$)' 会把 /agent/assets/*.js 一并转给网关，dev 下直接白屏。
      // 正则用 (/|$) 同时匹配「前缀即完整路径」的接口（如 POST /agent/chat）。
      '^/agent/(chat|conversation|feedback|index|search|facts)(/|$)': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // 登录/静默续期与其余 /v1 接口，复用网关鉴权链路
      '^/(auth|v1)(/|$)': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  // 子应用资源必须带自己的前缀，否则内嵌时与基座静态资源互相覆盖
  base: '/agent',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
