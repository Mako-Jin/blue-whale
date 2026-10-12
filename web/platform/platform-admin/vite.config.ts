import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunDevPreamble } from 'common-config/vite';

/** 子应用名。**必须与基座 `src/micro/apps.ts` 里注册的名字逐字一致。** */
const APP_NAME = 'platform-admin';

const lessVariables = fileURLToPath(
  new URL('./src/styles/variables.less', import.meta.url),
).replace(/\\/g, '/');

export default defineConfig(({ command }) => ({
  // 生产构建带自己的路径前缀，开发期保持 `/`（加前缀会让 HMR 客户端脚本找不到，热更新静默失效）
  base: command === 'build' ? '/admin/' : '/',
  plugins: [react(), qiankun(APP_NAME, { useDevMode: true }), qiankunDevPreamble()],
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
    // 3000 基座 / 3100 工具包 playground / 3200 日志台 / 3300 事件台 / 3400 三维，这里用 3500
    port: 3500,
    host: true,
    // 端口写死在基座的 entry 里，必须锁住：被占用时 vite 会顺延到 3501，
    // 而基座仍去 3500 拉，结果是"基座报加载失败、子应用这边一切正常"
    strictPort: true,
    cors: true,
    origin: '//localhost:3500',
    // 独立运行时把 /api 代理到后端；被基座加载时请求走基座页面的 /api，
    // 两条路径都通，因此代码里始终只写相对路径
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
