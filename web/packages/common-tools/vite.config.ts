import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

/**
 * 构建 + 测试配置。
 *
 * 说明：本包在工作区内是被「源码引用」的（package.json 的 main/exports 均指向 src），
 * 因此 lib 构建主要用于产物体积评估与将来的对外发布，不参与日常开发链路。
 */
export default defineConfig({
  build: {
    lib: {
      entry: fileURLToPath(new URL('./src/index.ts', import.meta.url)),
      name: 'CommonTools',
      fileName: 'common-tools',
      formats: ['es'],
    },
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
    target: 'es2022',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
    },
  },
});
