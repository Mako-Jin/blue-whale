import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm', 'cjs'],
  dts: true,
  sourcemap: true,
  clean: true,
  // 与宿主共享同一份工具库实例（Logger 全局配置、事件总线单例都依赖单实例）
  external: ['@bluewhale/whale-common-tools'],
});
