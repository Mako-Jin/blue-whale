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
    // 独立运行（非基座内嵌）时，把论坛/文件接口转发到网关。
    // 注意不能写成 `^/forum(/|$)`：子应用 base 本身就是 `/forum`，那样连
    // `GET /forum/`（qiankun 拉取的入口 HTML）、`/forum/src/*`、`/forum/@vite/client`
    // 一起被转发到网关，入口直接 401，基座与独立运行都是白屏且无报错。
    // 因此只匹配论坛真实的一级接口段。
    proxy: {
      '^/forum/(meta|board|thread|post|hot|search|tag|profile|my|follow|exp|appeal|report|reaction|admin|internal)(/|$)':
        {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      '^/file(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  base: '/forum',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
