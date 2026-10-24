import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 开发态网关地址（whale-gateway 默认 8080）
const GATEWAY_TARGET = 'http://127.0.0.1:8080';

// 网关一级前缀：与 whale-gateway 路由保持一致，新增服务时同步补充
const GATEWAY_PREFIXES = [
  '/auth',
  '/user',
  '/dept',
  '/role',
  '/menu',
  '/permission',
  '/erp',
  '/mes',
  '/pdm',
  '/oa',
  '/im',
  '/workflow',
  '/form',
  '/file',
  '/alert',
  '/monitor',
  '/audit',
  '/agent',
  '/job',
  '/pay',
  '/config',
];

interface ProxyRequest {
  method?: string;
  url?: string;
  headers: { accept?: string };
}

// 基座自身承载 /erp、/oa、/mes、/pdm 等 qiankun 路由；浏览器文档导航（Accept 含 text/html）
// 必须旁路给 vite SPA fallback，否则会被转发到网关导致子应用白屏
const gatewayBypass = (req: ProxyRequest): string | undefined =>
  req.method === 'GET' && req.headers.accept?.includes('text/html') ? req.url : undefined;

const proxy = Object.fromEntries(
  GATEWAY_PREFIXES.map((prefix) => [
    `^${prefix}(/|$)`,
    // ws:true 让 IM 子应用的 /im/ws 握手经基座端口透传到网关
    { target: GATEWAY_TARGET, changeOrigin: true, ws: true, bypass: gatewayBypass },
  ]),
);

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8000,
    cors: true,
    proxy,
  },
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
