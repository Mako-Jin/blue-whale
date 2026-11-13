import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// 开发态网关地址（whale-service-gateway 默认 8080）
const GATEWAY_TARGET = 'http://127.0.0.1:8080';

// 网关一级前缀：与 whale-service-gateway 路由保持一致，新增服务时同步补充
const GATEWAY_PREFIXES = [
  '/v1',
  '/auth',
  '/user',
  '/dept',
  '/role',
  '/menu',
  '/permission',
  '/erp',
  '/mes',
  '/pdm',
  '/pm',
  '/contract',
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
  '/music',
  '/scene',
  '/iot',
  '/twin',
  // 员工论坛（Wave 6.43）：子应用 base 与 API 一级前缀同为 /forum，
  // 基座必须转发（文档导航由 gatewayBypass 旁路给 SPA），否则内嵌场景下接口落到前端回退
  '/forum',
  // 以下四个此前缺失：2D 子应用各自 dev 端口自代理，所以没人发现；
  // 但 3D 城（/world）与基座同源取数时有 CRM/MALL/LMS/DOC 的概览接口，
  // 不补就会落到 SPA 回退——HTTP 200 + 返回 index.html，牌坊全判「未接通」
  '/crm',
  '/mall',
  '/lms',
  '/doc',
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
    rollupOptions: {
      output: {
        // vendor 拆分（T-10）：react/antd 系依赖单独成 chunk，
        // 业务迭代时 vendor hash 不变可命中浏览器缓存，且与业务代码并行加载
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined;
          if (
            /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|@remix-run|scheduler)[\\/]/.test(
              id,
            )
          ) {
            return 'vendor-react';
          }
          if (/[\\/]node_modules[\\/](antd|@ant-design|@rc-component|rc-|dayjs)/.test(id)) {
            return 'vendor-antd';
          }
          return undefined;
        },
      },
    },
  },
});
