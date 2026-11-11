import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

// 微应用名必须与基座 apps.ts 的 `name` 一致（qiankun 按 name 判定生命周期归属）
const microAppName = 'whale-agent';

/**
 * qiankun 内嵌修复（仅 dev 生效）：
 *
 * @vitejs/plugin-react 在 dev 下会向 head 注入内联 `<script type="module">` 形式的
 * react-refresh 预导脚本（import injectIntoGlobalHook ...）。vite-plugin-qiankun 的
 * ESM→经典脚本改写选择器是 `body script[type=module], head script[crossorigin=""]`，
 * 恰好漏掉这个「head 内、无 crossorigin」的内联脚本。基座 qiankun 的 import-html-entry
 * 不支持 ESM，会把它按经典脚本 eval，控制台恒报
 * "SyntaxError: Cannot use import statement outside a module"（HMR 预导失效，渲染不受影响）。
 *
 * 本插件把该内联脚本改写为经典脚本 + 动态 import，与 vite-plugin-qiankun 对 src 脚本的
 * 处理完全一致：qiankun 沙箱内用 proxy 注入的 public path 解析 URL，独立运行时回落
 * 文档地址。改写必须先于 main.tsx 的模块求值设置 $RefreshReg$/$RefreshSig$——
 * 本脚本在文档序上先于入口脚本执行、且 react-refresh 是单文件小模块，实践中先完成。
 */
function qiankunReactPreambleFix(): Plugin {
  // import 声明：import { a, b as c } from "url" / import d from 'url' / import * as n from "url" / import "url"
  const IMPORT_RE =
    /^import\s+(?:(\{[^}]*\})|\*\s+as\s+(\w+)|(\w+))?\s*(?:,\s*(?:(\{[^}]*\})|(\w+))\s*)?from\s*["']([^"']+)["'];?\s*$/gm;
  // 内联 module 脚本（无 src；带 src 的 module 脚本由 vite-plugin-qiankun 处理）
  const INLINE_MODULE_RE = /<script type="module">([\s\S]*?)<\/script>/g;

  /** 把解构子句里的 `{ a as b, c }` 归一为解构绑定文本 */
  function namedBindings(clause: string): string {
    return clause
      .slice(1, -1)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => s.replace(/\s+as\s+/, ': '))
      .join(', ');
  }

  return {
    name: 'qiankun-react-preamble-fix',
    apply: 'serve',
    transformIndexHtml: {
      order: 'post',
      handler(html: string) {
        return html.replace(INLINE_MODULE_RE, (whole, body: string) => {
          if (!/\bimport\b/.test(body)) return whole;
          const imports: { bindings: string; url: string }[] = [];
          const rest = body.replace(IMPORT_RE, (_m, named1, ns, def1, named2, def2, url) => {
            const parts: string[] = [];
            if (named1) parts.push(namedBindings(named1));
            if (named2) parts.push(namedBindings(named2));
            if (def1) parts.push(`default: ${def1}`);
            if (def2) parts.push(`default: ${def2}`);
            if (ns) parts.push(`...${ns}`);
            imports.push({ bindings: parts.length ? `const {${parts.join(', ')}} = mod;` : '', url });
            return '';
          });
          // 仅处理「单条静态 import」形态（plugin-react 预导即此形态）；其余形态保持原样
          if (imports.length !== 1 || !imports[0].url) return whole;
          // 与 vite-plugin-qiankun 的 publicPath 注入保持一致（沙箱内动态改写资源前缀）
          const publicPath = "(window.proxy ? (window.proxy.__INJECTED_PUBLIC_PATH_BY_QIANKUN__ + '..') : '')";
          const { bindings, url } = imports[0];
          return (
            '<script>\n'
            + `import(${publicPath} + '${url}')`
            + `.then(function (mod) {\n${bindings}\n${rest.trim()}\n});\n`
            + '</script>'
          );
        });
      },
    },
  };
}

export default defineConfig({
  plugins: [
    react(),
    qiankun(microAppName, {
      useDevMode: true,
    }),
    qiankunReactPreambleFix(),
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
