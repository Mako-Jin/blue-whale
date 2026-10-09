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
    // 产物落进 playground/dist —— 与包内其它构建产物同名，
    // 可直接复用共享 ESLint 配置里的 `**/dist/**` 忽略规则与 .gitignore，
    // 无需再为它单独加例外。
    outDir: fileURLToPath(new URL('./playground/dist', import.meta.url)),
    emptyOutDir: true,
  },
});
