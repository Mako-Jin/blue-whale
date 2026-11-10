import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

const microAppName = 'whale-im';

// 开发态网关地址（whale-gateway 默认 8080）
const GATEWAY_TARGET = 'http://127.0.0.1:8080';

interface ProxyRequest {
  method?: string;
  url?: string;
  headers: { accept?: string };
}

// `/im` 同时是子应用路由与后端 API 前缀：只有后端接口需要转发网关，
// 文档导航与静态资源（qiankun 拉入口时 Accept 为 `*/*`）必须旁路给 vite，否则会 401 白屏
const IM_API_PREFIXES = ['/im/conversations', '/im/agent-runs', '/im/ws-ticket', '/im/ws'];

const isImApi = (pathname: string): boolean =>
  IM_API_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));

const gatewayBypass = (req: ProxyRequest): string | undefined => {
  const pathname = (req.url ?? '').split('?')[0];
  if (req.method === 'GET' && pathname.startsWith('/im') && !isImApi(pathname)) {
    return req.url;
  }
  return req.method === 'GET' && req.headers.accept?.includes('text/html') ? req.url : undefined;
};

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
    port: 8107,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    proxy: {
      // REST 与 WS 升级统一转发到网关；ws:true 保证 /im/ws 握手可透传
      '^/im(/|$)': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
        ws: true,
        bypass: gatewayBypass,
      },
      '^/file(/|$)': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
        bypass: gatewayBypass,
      },
      '^/auth(/|$)': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
        bypass: gatewayBypass,
      },
      '^/v1(/|$)': {
        target: GATEWAY_TARGET,
        changeOrigin: true,
        bypass: gatewayBypass,
      },
    },
  },
  base: '/im',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
