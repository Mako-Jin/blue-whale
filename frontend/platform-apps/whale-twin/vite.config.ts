import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import path from 'node:path';

const microAppName = 'whale-twin';

export default defineConfig({
  plugins: [
    react(),
    qiankun(microAppName, {
      useDevMode: true,
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    // 端口唯一权威清单见 docs/service-catalog.md §七（qiankun 子应用区段）
    // 8116 已被 whale-party 占用，孪生前端自 Wave 6.03 起用 8122
    port: 8122,
    cors: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    // 独立运行（非基座内嵌）时，把 /twin 与 /file 前缀请求转发到网关：
    // /twin 走孪生服务；/file 走文件服务（GLB/GLTF 预览直链）
    proxy: {
      '^/twin(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '^/file(/|$)': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  base: '/twin',
  build: {
    target: 'esnext',
    sourcemap: false,
    rollupOptions: {
      output: {
        // three 全家桶（含 addons）单独拆 chunk，业务主包不再被 600KB+ 渲染库拖累
        manualChunks(id) {
          if (id.includes('node_modules/three') || id.includes('node_modules/.pnpm/three')) {
            return 'three';
          }
        },
      },
    },
  },
});
