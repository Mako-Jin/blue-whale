import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm', 'cjs'],
  dts: true,
  sourcemap: true,
  clean: true,
  // 由应用统一提供单例，避免把事件总线/Logger 打进本包造成多副本
  external: ['@bluewhale/whale-common-tools'],
});
