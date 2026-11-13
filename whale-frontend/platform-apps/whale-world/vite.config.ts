import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunReactPreambleFix } from '../../vite-plugins/vite-qiankun-preamble';
import path from 'node:path';

const microAppName = 'whale-world';

const GATEWAY = 'http://127.0.0.1:8080';

/**
 * 走网关的代理表。
 *
 * <p>网关不剥前缀（服务自持一级前缀，如 {@code @RequestMapping("/erp/bi/cockpit")} 就是对外路径），
 * 所以这里只做原样转发。内嵌基座时由基座代理；独立跑 8124 / 预览跑 8125 时必须自己代理，
 * 否则请求落到 SPA 回退页，拿到的是 index.html 而不是 JSON——表现为「所有牌都说未接通」，
 * 但网络面板里 HTTP 状态是 200，极易误判。</p>
 */
const API_PROXY = {
  // 城民问话
  '^/agent(/|$)': { target: GATEWAY, changeOrigin: true },
  // 同城频道：/im/ws-ticket 换票 + /im/world 升级长连接。
  // ws:true 必须显式打开，否则握手请求被当成普通 HTTP 回退页处理，
  // 表现为「连不上就退回单机」，画面里其他玩家全是本地模拟出来的。
  //
  // 已知限制：vite preview（8125）不转发 WS 升级（只有 dev 与基座 8000 会转发），
  // 所以预览下 HTTP 换票正常、长连接仍然连不上——这是 vite preview 自身的行为，
  // 不是配置漏项，别在这儿反复加参数。e2e 跑在 8125 上，因此走的是本地模拟那条路；
  // 联机本身由 tools/world-ws.mjs 单独验（直连 8080 与经 8124 各跑一遍）。
  '^/im(/|$)': { target: GATEWAY, changeOrigin: true, ws: true },
  // 殿前牌坊取数：八座殿对应的八个业务系统
  '^/erp(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/mes(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/oa(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/crm(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/lms(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/mall(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/doc(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/iot(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/alert(/|$)': { target: GATEWAY, changeOrigin: true },
  '^/monitor(/|$)': { target: GATEWAY, changeOrigin: true },
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
      proxy: API_PROXY,
  },
  // 产物预览（8125，e2e 跑这上面）同样要能打到网关，否则「在线问数」冒烟只能跑 dev
  preview: {
    port: 8125,
    proxy: API_PROXY,
  },
  base: '/world',
  build: {
    target: 'esnext',
    sourcemap: false,
  },
});
