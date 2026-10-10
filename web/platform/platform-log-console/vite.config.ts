import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunDevPreamble } from 'common-config/vite';

/** 子应用名。**必须与基座 `src/micro/apps.ts` 里注册的名字逐字一致。** */
const APP_NAME = 'platform-log-console';

const lessVariables = fileURLToPath(
  new URL('./src/styles/variables.less', import.meta.url),
).replace(/\\/g, '/');

export default defineConfig(({ command }) => ({
  /**
   * 生产构建带子应用自己的路径前缀，开发期保持 `/`。
   *
   * 开发期**不能**加前缀：`vite-plugin-qiankun` 是靠 `base` 去定位 `/@vite/client`
   * 这个 HMR 客户端脚本来做路径改写的，加了前缀它就找不到，热更新会静默失效。
   */
  base: command === 'build' ? '/log-console/' : '/',
  plugins: [
    react(),
    /**
     * `useDevMode: true` 表示"开发期由基座跨端口加载本应用"。
     * 插件会把入口的 `<script type="module">` 改写成动态 `import()`，
     * 并在 `window` 上挂一个生命周期壳 —— 这一步不做，Vite 的 ESM 产物
     * qiankun 根本执行不了（它执行脚本的方式走的是 eval，跑不了 module script）。
     */
    qiankun(APP_NAME, { useDevMode: true }),
    /**
     * 补掉 qiankun 加载子应用时的一个必现错误：
     * React Fast Refresh 的 preamble 是内联 module 脚本，qiankun 用 eval 执行它会抛
     * `Cannot use import statement outside a module`，热更新随之失效。
     * 详见 `common-config/src/vite.js`。
     */
    qiankunDevPreamble(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
        additionalData: `@import "${lessVariables}";`,
      },
    },
  },
  server: {
    // 3100 已被 common-tools 的 playground 占用，这里用 3200
    port: 3200,
    host: true,
    /**
     * 端口写死在基座的 entry 里**必须**锁住 ——
     * 被占用时 vite 默认会顺延到 3201，而基座仍然去 3200 拉，结果是基座报加载失败、
     * 而子应用这边一切正常，排查方向一开始就是错的。宁可启动直接失败。
     */
    strictPort: true,
    /**
     * 允许基座（另一个端口）跨域拉取本应用的 HTML 与脚本。
     * 不开的话基座连入口 HTML 都拿不到，现象是"日志控制台加载失败"。
     *
     * `origin` 决定 vite 重写资源地址（如 HMR 客户端）时使用的前缀。
     */
    cors: true,
    origin: '//localhost:3200',
    /**
     * 把 /api 代理到后端。
     *
     * 独立运行时靠它把请求打到后端；被基座加载时，请求走的是**基座页面**的
     * `/api`，由基座的代理转发 —— 两条路径都通，子应用代码里始终只写相对路径。
     *
     * 走代理而不是让浏览器直接跨域：开发期请求与服务端同源，
     * 因此 Cookie、CORS 预检、Referer 这些跨域才会出现的问题一个都不会分散注意力 ——
     * 排查日志功能时被跨域问题挡住是最不划算的事。
     */
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    target: 'es2022',
    chunkSizeWarningLimit: 1500,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
    },
  },
}));
