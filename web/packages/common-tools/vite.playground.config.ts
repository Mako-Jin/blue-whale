import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

/**
 * Playground：把 logger 的真实控制台输出镜像渲染到页面上，
 * 用来直观检查颜色、格式、层级与主题效果。
 *
 * 刻意不引入 React —— 本包与框架无关，演示也不该把它绑到某个框架上。
 */
export default defineConfig({
  root: fileURLToPath(new URL('./playground', import.meta.url)),
  server: {
    port: 3100,
    host: '127.0.0.1',
    open: false,
  },
  build: {
    outDir: fileURLToPath(new URL('./dist-playground', import.meta.url)),
    emptyOutDir: true,
  },
});
